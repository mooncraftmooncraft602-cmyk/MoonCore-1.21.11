package com.mooncore.modules.customitem.forge;

import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Inférence <b>100% Java</b> d'un petit GPT (archi GPT-2) entraîné hors-ligne — <b>aucune dépendance runtime</b>
 * (pas de Python, pas de sidecar, pas de natif). Charge le binaire {@code forge-gpt.bin} (exporté par
 * {@code tools/forge-model/export.py}) et exécute le forward pass + génération greedy dans la JVM du serveur.
 *
 * <p>Le modèle décide la rampe de couleurs d'un item à partir de son nom (« Épée du Vent → … »). Réimplémente
 * fidèlement : embeddings token+position, blocs {LayerNorm pré-norm, self-attention causale multi-tête, MLP GELU},
 * weight tying tête/embedding. Modèle minuscule → génération en quelques ms/dizaines de ms.</p>
 */
public final class GptInference {

    private static final float LN_EPS = 1e-5f;

    private final int vocab, block, nLayer, nHead, nEmbd, headDim;
    private final float[] wte, wpe, lnfW, lnfB;
    // par couche
    private final float[][] ln1W, ln1B, attnW, attnB, projW, projB, ln2W, ln2B, fcW, fcB, fcprojW, fcprojB;
    private final char[] itos;
    private final Map<Character, Integer> stoi = new HashMap<>();

    private GptInference(ByteBuffer b) {
        byte[] magic = new byte[4];
        b.get(magic);
        if (!(magic[0] == 'M' && magic[1] == 'G' && magic[2] == 'P' && magic[3] == 'T')) {
            throw new IllegalArgumentException("forge-gpt.bin : entête invalide");
        }
        b.getInt();                          // version
        vocab = b.getInt(); block = b.getInt(); nLayer = b.getInt(); nHead = b.getInt(); nEmbd = b.getInt();
        b.getInt();                          // bias flag (toujours présent dans notre export)
        headDim = nEmbd / nHead;
        int E = nEmbd;

        wte = floats(b, vocab * E);
        wpe = floats(b, block * E);
        ln1W = new float[nLayer][]; ln1B = new float[nLayer][];
        attnW = new float[nLayer][]; attnB = new float[nLayer][];
        projW = new float[nLayer][]; projB = new float[nLayer][];
        ln2W = new float[nLayer][]; ln2B = new float[nLayer][];
        fcW = new float[nLayer][]; fcB = new float[nLayer][];
        fcprojW = new float[nLayer][]; fcprojB = new float[nLayer][];
        for (int i = 0; i < nLayer; i++) {
            ln1W[i] = floats(b, E);      ln1B[i] = floats(b, E);
            attnW[i] = floats(b, 3 * E * E); attnB[i] = floats(b, 3 * E);
            projW[i] = floats(b, E * E);  projB[i] = floats(b, E);
            ln2W[i] = floats(b, E);      ln2B[i] = floats(b, E);
            fcW[i] = floats(b, 4 * E * E); fcB[i] = floats(b, 4 * E);
            fcprojW[i] = floats(b, E * 4 * E); fcprojB[i] = floats(b, E);
        }
        lnfW = floats(b, E); lnfB = floats(b, E);
        itos = new char[vocab];
        for (int i = 0; i < vocab; i++) {
            char c = (char) b.getInt();
            itos[i] = c;
            stoi.put(c, i);
        }
    }

    /** Charge le modèle depuis un fichier, ou null si absent/illisible. */
    public static GptInference load(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file.toPath())).order(ByteOrder.LITTLE_ENDIAN);
            return new GptInference(b);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static float[] floats(ByteBuffer b, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) a[i] = b.getFloat();
        return a;
    }

    public int blockSize() { return block; }
    public int vocabSize() { return vocab; }

    /** Logits de la dernière position (exposé pour les tests de conformité Java↔Python). */
    float[] lastLogits(int[] ids) { return forwardLast(ids); }

    // ----------------------------- forward -----------------------------

    /** Logits (vocab) à la dernière position de la séquence {@code ids}. */
    private float[] forwardLast(int[] ids) {
        int T = Math.min(ids.length, block);
        int off = ids.length - T;
        int E = nEmbd;
        float[][] x = new float[T][E];
        for (int t = 0; t < T; t++) {
            int id = ids[off + t];
            for (int e = 0; e < E; e++) x[t][e] = wte[id * E + e] + wpe[t * E + e];
        }
        for (int l = 0; l < nLayer; l++) {
            // --- attention ---
            float[][] xn = new float[T][];
            float[][] q = new float[T][], k = new float[T][], v = new float[T][];
            for (int t = 0; t < T; t++) {
                xn[t] = layerNorm(x[t], ln1W[l], ln1B[l]);
                float[] qkv = linear(xn[t], attnW[l], attnB[l], 3 * E);
                q[t] = slice(qkv, 0, E); k[t] = slice(qkv, E, E); v[t] = slice(qkv, 2 * E, E);
            }
            float[][] att = new float[T][E];
            float scale = (float) (1.0 / Math.sqrt(headDim));
            for (int h = 0; h < nHead; h++) {
                int hb = h * headDim;
                for (int t = 0; t < T; t++) {
                    float[] scores = new float[t + 1];
                    float max = Float.NEGATIVE_INFINITY;
                    for (int s = 0; s <= t; s++) {
                        float dot = 0f;
                        for (int d = 0; d < headDim; d++) dot += q[t][hb + d] * k[s][hb + d];
                        scores[s] = dot * scale;
                        if (scores[s] > max) max = scores[s];
                    }
                    float sum = 0f;
                    for (int s = 0; s <= t; s++) { scores[s] = (float) Math.exp(scores[s] - max); sum += scores[s]; }
                    for (int d = 0; d < headDim; d++) {
                        float acc = 0f;
                        for (int s = 0; s <= t; s++) acc += (scores[s] / sum) * v[s][hb + d];
                        att[t][hb + d] = acc;
                    }
                }
            }
            for (int t = 0; t < T; t++) {
                float[] y = linear(att[t], projW[l], projB[l], E);
                for (int e = 0; e < E; e++) x[t][e] += y[e];
            }
            // --- MLP ---
            for (int t = 0; t < T; t++) {
                float[] xn2 = layerNorm(x[t], ln2W[l], ln2B[l]);
                float[] hmid = linear(xn2, fcW[l], fcB[l], 4 * E);
                for (int j = 0; j < hmid.length; j++) hmid[j] = gelu(hmid[j]);
                float[] y = linear(hmid, fcprojW[l], fcprojB[l], E);
                for (int e = 0; e < E; e++) x[t][e] += y[e];
            }
        }
        float[] xf = layerNorm(x[T - 1], lnfW, lnfB);
        float[] logits = new float[vocab];     // lm_head = wte (tied)
        for (int vix = 0; vix < vocab; vix++) {
            float acc = 0f;
            for (int e = 0; e < E; e++) acc += wte[vix * E + e] * xf[e];
            logits[vix] = acc;
        }
        return logits;
    }

    /** Génère la suite du prompt en greedy (argmax) jusqu'à {@code stop} ou {@code maxNew} tokens. */
    public String generate(String prompt, int maxNew, char stop) {
        int[] ids = encode(prompt);
        if (ids.length == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int step = 0; step < maxNew; step++) {
            float[] logits = forwardLast(ids);
            int next = argmax(logits);
            char c = itos[next];
            if (c == stop) break;
            out.append(c);
            int[] nids = new int[ids.length + 1];
            System.arraycopy(ids, 0, nids, 0, ids.length);
            nids[ids.length] = next;
            ids = nids;
        }
        return out.toString();
    }

    private int[] encode(String s) {
        int[] tmp = new int[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            Integer id = stoi.get(s.charAt(i));
            if (id != null) tmp[n++] = id;
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    // ----------------------------- maths -----------------------------

    private static float[] linear(float[] in, float[] w, float[] b, int outDim) {
        int inDim = in.length;
        float[] out = new float[outDim];
        for (int o = 0; o < outDim; o++) {
            float acc = b != null ? b[o] : 0f;
            int base = o * inDim;
            for (int i = 0; i < inDim; i++) acc += in[i] * w[base + i];
            out[o] = acc;
        }
        return out;
    }

    private static float[] layerNorm(float[] in, float[] g, float[] b) {
        int n = in.length;
        float mean = 0f;
        for (float v : in) mean += v;
        mean /= n;
        float var = 0f;
        for (float v : in) { float d = v - mean; var += d * d; }
        var /= n;
        float inv = (float) (1.0 / Math.sqrt(var + LN_EPS));
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = (in[i] - mean) * inv * g[i] + b[i];
        return out;
    }

    /** GELU exacte (erf) — comme {@code torch.nn.functional.gelu} par défaut. */
    private static float gelu(float x) {
        return 0.5f * x * (1f + erf((float) (x / Math.sqrt(2.0))));
    }

    /** Approximation d'erf (Abramowitz-Stegun 7.1.26), erreur ~1e-7. */
    private static float erf(float x) {
        float sign = x < 0 ? -1f : 1f;
        x = Math.abs(x);
        float t = 1f / (1f + 0.3275911f * x);
        float y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * (float) Math.exp(-x * x);
        return sign * y;
    }

    private static float[] slice(float[] a, int from, int len) {
        float[] out = new float[len];
        System.arraycopy(a, from, out, 0, len);
        return out;
    }

    private static int argmax(float[] a) {
        int best = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[best]) best = i;
        return best;
    }
}
