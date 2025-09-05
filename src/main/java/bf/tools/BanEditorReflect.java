package bf.tools;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

import bf.tools.BanEditorReflect.TitMode;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked","rawtypes"})
public class BanEditorReflect {

    // ===================== CONFIG / FLAGS =====================
    public static boolean DEBUG = false;
    public static final boolean WRITE_BOM = true;
    public static final int MAX_JOGADORES_IMPORT = 30;

    // ===================== MAPAS / ALIASES =====================
    public static final Map<String,Integer> PAISES = new HashMap<>();
    public static final Map<String,String> PAISES_REV = new HashMap<>();
    public static final Map<String,Integer> POSICOES = new HashMap<>();
    public static final Map<Integer,String> POSICOES_REV = new HashMap<>();
    public static final Map<String,Integer> LADOS = new HashMap<>();
    public static final Map<Integer,String> LADOS_REV = new HashMap<>();
    public static final Map<String,Integer> CARACS = new HashMap<>();
    public static final Map<Integer,String> CARACS_REV = new HashMap<>();

    public static final String[] CSV_JOG_HEADERS = new String[]{
  "Nome","Idade","Nacionalidade","Posição","Características","Lado","Estrela","Top Mundial","Titular"};

    // Candidatos conhecidos de campo "Titular/Colete" encontrados em dumps de .ban
    public static final String[] TITULAR_CANDIDATES = new String[] { "tid", "t", "f", "sid", "aid" };

    // seta um campo numérico como 1/0
    public static void setAsInt(Object obj, Field f, boolean value) throws Exception {
        f.setAccessible(true);
        Class<?> t = f.getType();
        int v = value ? 1 : 0;
        if (t == int.class || t == Integer.class)      f.set(obj, v);
        else if (t == short.class || t == Short.class) f.set(obj, (short) v);
        else if (t == byte.class  || t == Byte.class)  f.set(obj, (byte) v);
        else if (t == long.class  || t == Long.class)  f.set(obj, (long) v);
        else if (t == boolean.class || t == Boolean.class) f.set(obj, value);
        else {
            // último recurso: tenta converter string/number
            try { f.set(obj, v); } catch (Throwable ignore) {}
        }
    }

    // === campos detectados em tempo de execução ===
    public static String FIELD_CLUBE_NOME = "e";
    public static String FIELD_CLUBE_PAIS = "a";
    public static String FIELD_CLUBE_NIVEL = "c";
    public static String FIELD_CLUBE_ESTADIO = "f";
    public static String FIELD_CLUBE_CAP = "g";
    public static String FIELD_CLUBE_TREINADOR = "h";
    public static String FIELD_CLUBE_LISTA = "l";

    public static String FIELD_J_NOME = "a";
    public static String FIELD_J_IDADE = "d";
    public static String FIELD_J_NAC = "c";
    public static String FIELD_J_POS = "e";
    public static String FIELD_J_CAR1 = "g";
    public static String FIELD_J_CAR2 = "h";
    public static String FIELD_J_LADO = "i";
    public static String FIELD_J_ESTRELA = "b";
    public static String FIELD_J_TOP = "j";
    public static String FIELD_J_TITULAR = null; // detectado

    // ===================== MAIN =====================
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return;
        }

        List<String> a = Arrays.asList(args);
        DEBUG = a.contains("--debug");

        if (a.contains("--exportPlayersIn")) {
            Path in = Paths.get(nextArg(a, "--exportPlayersIn"));
            Path out = Paths.get(nextArg(a, "--out"));
            exportPlayersBatch(in, out);
            return;
        }

        if (a.contains("--exportIn")) {
            Path in = Paths.get(nextArg(a, "--exportIn"));
            Path out = Paths.get(nextArg(a, "--out"));
            exportModeloBatch(in, out);
            return;
        }

        if (a.contains("--export")) {
            Path ban = Paths.get(nextArg(a, "--export"));
            Path out = a.contains("--out") ? Paths.get(nextArg(a, "--out")) : Paths.get(a.get(a.size()-1));
            exportModeloSingle(ban, out);
            return;
        }

        if (a.contains("--applyTransfers")) {
            Path transfers = Paths.get(nextArg(a, "--transfers"));
            Path inDir = Paths.get(nextArg(a, "--in"));
            Path outDir = Paths.get(nextArg(a, "--out"));
            applyTransfersNoCreate(transfers, inDir, outDir);
            return;
        }

        // Import único: <original.ban> <dados.csv> <saida.ban> [--debug]
        if (args.length >= 3) {
            Path banIn = Paths.get(args[0]);
            Path csv = Paths.get(args[1]);
            Path banOut = Paths.get(args[2]);
            processSingle(banIn, csv, banOut);
            return;
        }

        printHelp();
    }

    public static void printHelp() {
        System.out.println("Uso (único import):");
        System.out.println("  java BanEditorReflect <original.ban> <dados.csv> <saida.ban>");
        System.out.println();
        System.out.println("Uso (lote import):");
        System.out.println("  java BanEditorReflect --in <pasta_bans> --csv <pasta_csvs> --out <pasta_saida> [--debug]");
        System.out.println();
        System.out.println("Exportar modelo (único):");
        System.out.println("  java BanEditorReflect --export <arquivo.ban> --out <saida.csv> [--no-bom]");
        System.out.println("Exportar modelo (lote):");
        System.out.println("  java BanEditorReflect --exportIn <pasta_bans> --out <pasta_csvs> [--no-bom]");
        System.out.println();
        System.out.println("Exportar jogadores em um único CSV:");
        System.out.println("  java BanEditorReflect --exportPlayersIn <pasta_bans> --out <jogadores.csv> [--debug]");
        System.out.println();
        System.out.println("Transferências (sem criar jogadores):");
        System.out.println("  java BanEditorReflect --applyTransfers --transfers <transferencias.csv> --in <pasta_bans> --out <pasta_saida> [--debug]");
        System.out.println();
        System.out.println("Colunas esperadas (jogadores):");
        System.out.println("  Nome;Idade;Nacionalidade;Posição;Características;Lado;Estrela;Top Mundial;Titular");
    }

    // ===================== PROCESSOS PRINCIPAIS =====================

    public static void processSingle(Path banIn, Path csv, Path banOut) throws Exception {
        Object clube = lerBan(banIn);

        // Diagnóstico antes
        if (DEBUG) {
            System.out.println("=== Diagnóstico (antes) ===");
            diagClube(clube, true);
        }

        System.out.println("[OK] Classe raiz: " + clube.getClass().getName());

        // detectar campos estrela/top/titular
        detectBoolFields((List)get(clube, FIELD_CLUBE_LISTA));

        // Ler CSV e aplicar
        List<String[]> rows = lerCsv(csv);
        aplicarCsvNoObjeto(clube, rows);

        if (DEBUG) {
            System.out.println("--- Após aplicar CSV ---");
            diagClube(clube, true);
        }

        salvarBan(clube, banOut);
        System.out.println("[OK] Gerado: " + banOut);
    }

    public static void exportModeloSingle(Path ban, Path outCsv) throws Exception {
        Object clube = lerBan(ban);
        detectBoolFields((List)get(clube, FIELD_CLUBE_LISTA));

        List<String[]> linhas = new ArrayList<>();
        // Cabeçalho do bloco Clube + Jogadores
        linhas.add(new String[]{"Clube"});
        linhas.add(new String[]{"País", paisIdToNome((Integer)get(clube, FIELD_CLUBE_PAIS))});
        linhas.add(new String[]{"Nível", String.valueOf(getInt(clube, FIELD_CLUBE_NIVEL))});
        linhas.add(new String[]{"Estádio", String.valueOf(get(clube, FIELD_CLUBE_ESTADIO))});
        linhas.add(new String[]{"Capacidade", String.valueOf(getInt(clube, FIELD_CLUBE_CAP))});
        linhas.add(new String[]{"Treinador", String.valueOf(get(clube, FIELD_CLUBE_TREINADOR))});
        linhas.add(new String[]{"Jogadores"});

        // header jogadores
        linhas.add(new String[]{
                "Nome","Idade","Nacionalidade","Posição","Características","Lado","Estrela","Top Mundial","Titular"
        });

        List<Object> js = (List)get(clube, FIELD_CLUBE_LISTA);
        if (js != null) {
            TitMode titMode = detectTitularModeForExport(js); // <<< NOVO
            for (int i = 0; i < js.size(); i++) {
                Object j = js.get(i);
                linhas.add(exportJogadorLinha(j, i, titMode)); // <<< passa índice e modo
            }
        }


        escreverCsv(outCsv, linhas, WRITE_BOM);
        System.out.println("[OK] Export modelo: " + outCsv);
    }

    public static void exportModeloBatch(Path inDir, Path outDir) throws Exception {
        Files.createDirectories(outDir);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(inDir, "*.ban")) {
            for (Path ban : ds) {
                Path out = outDir.resolve(replaceExt(ban.getFileName().toString(), ".csv"));
                exportModeloSingle(ban, out);
            }
        }
    }

    public static void exportPlayersBatch(Path inDir, Path outCsv) throws Exception {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"Clube","Arquivo",".Pos","Nome","Idade","Nacionalidade","Posição","Características","Lado","Estrela","Top Mundial","Titular"});
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(inDir, "*.ban")) {
            for (Path ban : ds) {
                Object clube = lerBan(ban);
                List<Object> js = (List)get(clube, FIELD_CLUBE_LISTA);
                detectBoolFields(js);
                TitMode titMode = detectTitularModeForExport(js); // <<< NOVO

                String clubeNome = String.valueOf(get(clube, FIELD_CLUBE_NOME));
                for (int idx = 0; idx < js.size(); idx++) {
                    Object j = js.get(idx);
                    String[] ln = exportJogadorLinha(j, idx, titMode); // <<< passa índice e modo
                    // prepend clube/arquivo/pos
                    String[] full = new String[ln.length + 3];
                    full[0] = clubeNome;
                    full[1] = ban.getFileName().toString();
                    full[2] = String.valueOf(idx);
                    System.arraycopy(ln, 0, full, 3, ln.length);
                    out.add(full);
                }

            }
        }
        escreverCsv(outCsv, out, WRITE_BOM);
        System.out.println("[OK] Export players (lote): " + outCsv);
    }

    public static void applyTransfersNoCreate(Path transfersFile, Path inDir, Path outDir) throws Exception {
        Files.createDirectories(outDir);

        Map<String,Path> mapaClubes = new HashMap<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(inDir, "*.ban")) {
            for (Path ban : ds) {
                Object club = lerBan(ban);
                String nome = String.valueOf(get(club, FIELD_CLUBE_NOME));
                mapaClubes.put(normKey(nome), ban);
                if (DEBUG) System.out.println("[LOAD] Clube: " + nome + " <- " + ban.getFileName());
            }
        }

        List<String[]> trs = lerCsv(transfersFile);
        trs.removeIf(r -> r.length==0);
        int ok=0, fail=0, skip=0;

        // clube -> objeto carregado
        Map<String,Object> cache = new HashMap<>();
        Set<String> toSave = new LinkedHashSet<>();

        for (String[] r : trs) {
            if (r.length < 3) { skip++; continue; }
            String origem = r[0].trim();
            String destino = r[1].trim();
            String jogador = r[2].trim();
            if (origem.isEmpty() || destino.isEmpty() || jogador.isEmpty()) { skip++; continue; }

            Path pOrig = mapaClubes.get(normKey(origem));
            Path pDest = mapaClubes.get(normKey(destino));
            if (pOrig == null) { System.out.println("[FAIL] Origem não encontrada: '"+origem+"' para '"+jogador+"'"); fail++; continue; }
            if (pDest == null) { System.out.println("[FAIL] Destino não encontrado: '"+destino+"' para '"+jogador+"'"); fail++; continue; }

            Object cOrig = cache.computeIfAbsent(pOrig.toString(), k -> safeReadBan(pOrig));
            Object cDest = cache.computeIfAbsent(pDest.toString(), k -> safeReadBan(pDest));
            if (cOrig == null || cDest == null) { fail++; continue; }

            List<Object> listOrig = (List)get(cOrig, FIELD_CLUBE_LISTA);
            List<Object> listDest = (List)get(cDest, FIELD_CLUBE_LISTA);
            detectBoolFields(listOrig); // garante campos

            Object mov = detachPlayerByName(listOrig, jogador);
            if (mov == null) {
                System.out.println("[FAIL] Jogador não encontrado em '"+origem+"': " + jogador);
                fail++; continue;
            }

            // manter Estrela/Top/Titular já estão no objeto mov
            listDest.add(mov);

            toSave.add(pOrig.toString());
            toSave.add(pDest.toString());
            ok++;
            if (DEBUG) System.out.println("[OK] " + jogador + ": " + origem + " -> " + destino);
        }

        // salvar: copia os não tocados, regrava os alterados
        Set<String> already = new HashSet<>();
        try (DirectoryStream<Path> ds2 = Files.newDirectoryStream(inDir, "*.ban")) {
            for (Path ban : ds2) {
                Path out = outDir.resolve(ban.getFileName().toString());
                if (toSave.contains(ban.toString())) {
                    if (already.contains(ban.toString())) continue;
                    Object c = cache.get(ban.toString());
                    salvarBan(c, out);
                    already.add(ban.toString());
                } else {
                    Files.copy(ban, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        System.out.println("\n=== RESUMO TRANSFERÊNCIAS (sem criação) ===");
        System.out.println("OK:   " + ok);
        System.out.println("FAIL: " + fail);
        System.out.println("SKIP: " + skip);
        System.out.println("Saída: " + outDir.toAbsolutePath());
    }

    // ===================== EXPORT/LINHA JOGADOR =====================

    public static String[] exportJogadorLinha(Object j, int idx, TitMode titMode) throws Exception {
        String nome  = String.valueOf(get(j, FIELD_J_NOME));
        int    idade = getInt(j, FIELD_J_IDADE);
        int    nacId = getInt(j, FIELD_J_NAC);
        int    posId = getInt(j, FIELD_J_POS);
        int    car1 = getInt(j, FIELD_J_CAR1);
        int    car2 = getInt(j, FIELD_J_CAR2);
        int    ladoId = getInt(j, FIELD_J_LADO);

        String caracs = joinNonEmpty(caracNome(car1), caracNome(car2));

        boolean estrela = getBool(j, FIELD_J_ESTRELA);
        boolean top     = getBool(j, FIELD_J_TOP);
        boolean titular = isTitularForExport(idx, j, titMode); // <<< CONSISTENTE

        return new String[]{
            nome,
            String.valueOf(idade),
            paisIdToNome(nacId),
            posNome(posId),
            caracs,
            ladoNome(ladoId),
            boolAsSim(estrela),
            boolAsSim(top),
            boolAsSim(titular)
        };
    }


    public static String boolAsSim(boolean v){ return v ? "Sim" : " "; }
    public static String boolAsSimTitan(boolean v){ return v ? "Sim" : " "; }

    // ===================== CSV → OBJETO (import) =====================

    @SuppressWarnings("unchecked")
    public static void aplicarCsvNoObjeto(Object clube, List<String[]> rows) throws Exception {
        // --- normalizador curtinho ---
        java.text.Normalizer.Form _NFKD = java.text.Normalizer.Form.NFKD;
        java.util.function.Function<String,String> norm = s -> {
            if (s == null) return "";
            s = s.replace("\uFEFF","").replace("\u200E","").replace("\u200F",""); // BOM/dir. invisíveis
            String t = java.text.Normalizer.normalize(s.trim(), _NFKD).replaceAll("\\p{M}+", "");
            return t.toLowerCase().replaceAll("\\s+"," ");
        };

        // ===== 1) BLOCO "CLUBE" (Chave;Valor) =====
        int i = 0;
        while (i < rows.size()) {
            String[] r = rows.get(i);
            if (r == null || r.length == 0) { i++; continue; }

            // títulos: olhe APENAS a primeira célula, mesmo com ;;; no resto
            String first = safe(r, 0);
            String firstN = norm.apply(first);

            if (firstN.equals("clube")) { i++; continue; }
            if (firstN.equals("jogadores")) { i++; break; } // entra no bloco de jogadores

            // pares chave/valor do clube
            if (r.length >= 2) {
                String key = safe(r, 0);
                String val = safe(r, 1);
                String nk  = norm.apply(key);

                if (nk.equals("pais"))                    { set(clube, FIELD_CLUBE_PAIS,      paisNomeToId(val)); }
                else if (nk.equals("nivel"))              { set(clube, FIELD_CLUBE_NIVEL,     parseIntSafe(val)); }
                else if (nk.equals("estadio"))            { set(clube, FIELD_CLUBE_ESTADIO,   val);               }
                else if (nk.equals("capacidade"))         { set(clube, FIELD_CLUBE_CAP,       parseIntSafe(val)); }
                else if (nk.equals("treinador"))          { set(clube, FIELD_CLUBE_TREINADOR, val);               }
            }
            i++;
        }

        // ===== 2) BLOCO "JOGADORES" =====
        List<Object> nova = new ArrayList<>();
        List<Object> origList = (List<Object>) get(clube, FIELD_CLUBE_LISTA);
        if (origList == null) origList = new ArrayList<>();

        // Descobre dinamicamente Estrela/Top/Titular
        detectBoolFields(origList);

        // a) localizar a linha de cabeçalho dos jogadores
        int headerIdx = -1;

        // busca a partir de i (logo após "Jogadores")
        for (int k = i; k < rows.size(); k++) {
            String[] r = rows.get(k);
            if (r == null || r.length == 0) continue;

            // pula linhas-título perdidas
            String first = safe(r,0);
            String fn = norm.apply(first);
            if (fn.equals("clube") || fn.equals("jogadores")) continue;

            // é header se QUALQUER célula normalize para "nome"
            boolean isHeader = false;
            for (String cell : r) {
                if (cell != null && norm.apply(cell).equals("nome")) { isHeader = true; break; }
            }
            if (isHeader) { headerIdx = k; break; }
        }

        // fallback: se não achou header depois de i, procure no arquivo inteiro
        if (headerIdx < 0) {
            for (int k = 0; k < rows.size(); k++) {
                String[] r = rows.get(k);
                if (r == null || r.length == 0) continue;
                boolean isHeader = false;
                for (String cell : r) {
                    if (cell != null && norm.apply(cell).equals("nome")) { isHeader = true; break; }
                }
                if (isHeader) { headerIdx = k; break; }
            }
        }

        if (headerIdx < 0) {
            if (DEBUG) System.out.println("[WARN] Não encontrei cabeçalho de jogadores no CSV; mantendo elenco original.");
            return; // não substitui elenco
        }

        // b) mapear índices das colunas por nome (ordem livre)
        String[] header = rows.get(headerIdx);
        int cNome=-1, cIdade=-1, cNac=-1, cPos=-1, cCarac=-1, cLado=-1, cEstrela=-1, cTop=-1, cTitular=-1;

        for (int idx = 0; idx < header.length; idx++) {
            String raw = safe(header, idx);
            String h = normKey(raw); // ex.: "Top Mundial" -> "topmundial"

            if (h.equals("nome"))                                   cNome = idx;
            else if (h.equals("idade"))                             cIdade = idx;
            else if (h.equals("nacionalidade") || h.equals("pais")) cNac = idx;
            else if (h.equals("posicao"))                           cPos = idx;
            else if (h.equals("caracteristicas") || h.equals("caracteristica") || h.equals("caracteristicas")) cCarac = idx;
            else if (h.equals("lado"))                              cLado = idx;
            else if (h.equals("estrela"))                           cEstrela = idx;

            // TOP MUNDIAL — aceitar várias formas
            else if (h.equals("topmundial") || h.equals("top") || h.equals("tm")) cTop = idx;

            // TITULAR / COLETE — aceitar ambas
            else if (h.equals("titular") || h.equals("colete"))     cTitular = idx;
        }

        // DEBUG: mostrar o mapeamento encontrado
        if (DEBUG) {
            System.out.println(String.format(
                "[HDR] idxs => nome=%d, idade=%d, nac=%d, pos=%d, carac=%d, lado=%d, estrela=%d, top=%d, titular=%d",
                cNome,cIdade,cNac,cPos,cCarac,cLado,cEstrela,cTop,cTitular
            ));
        }


        // c) processar linhas de jogadores (após o header)
        int start = headerIdx + 1;

        for (int li = start; li < rows.size(); li++) {
            String[] r = rows.get(li);
            if (r == null || r.length == 0) continue;

            // pula linhas vazias ou títulos perdidos
            if (r.length == 1) {
                String unico = safe(r,0);
                String nu = norm.apply(unico);
                if (unico.isEmpty() || nu.equals("clube") || nu.equals("jogadores")) continue;
            }

            Object ex = (!origList.isEmpty() ? origList.get(0) : null);
            if (ex == null) {
                if (DEBUG) System.out.println("[ERRO] BAN não possui exemplo de jogador para instanciar.");
                break;
            }

            // Nome (obrigatório)
            String nomeJogador = (cNome < r.length ? safe(r, cNome) : "");
            if (nomeJogador.isEmpty() || norm.apply(nomeJogador).equals("nome")) continue;

            Object j = ex.getClass().getDeclaredConstructor().newInstance();
            set(j, FIELD_J_NOME, nomeJogador);


            // Idade
            if (cIdade >= 0 && cIdade < r.length) {
                set(j, FIELD_J_IDADE, parseIntSafe(safe(r, cIdade)));
            }

            // Nacionalidade
            if (cNac >= 0 && cNac < r.length) {
                set(j, FIELD_J_NAC, paisNomeToId(safe(r, cNac)));
            }

            // Posição
            if (cPos >= 0 && cPos < r.length) {
                set(j, FIELD_J_POS, posNomeToId(safe(r, cPos)));
            }

            // Características
            int c1 = 0, c2 = 0;
            if (cCarac >= 0 && cCarac < r.length) {
                String carRaw = safe(r, cCarac);
                if (!carRaw.isEmpty()) {
                    String[] cs = carRaw.split(",\\s*");
                    if (cs.length > 0 && !cs[0].isEmpty()) c1 = caracNomeToId(cs[0]);
                    if (cs.length > 1 && !cs[1].isEmpty()) c2 = caracNomeToId(cs[1]);
                }
            }
            set(j, FIELD_J_CAR1, c1);
            set(j, FIELD_J_CAR2, c2);

            // Lado
            if (cLado >= 0 && cLado < r.length) {
                set(j, FIELD_J_LADO, ladoNomeToId(safe(r, cLado)));
            }

                // Estrela / Top / Titular
                String nome = (cNome < r.length ? safe(r, cNome) : "");
                boolean estrela = (cEstrela >= 0 && cEstrela < r.length) && parseBoolFlexible(safe(r, cEstrela));
                boolean top     = (cTop     >= 0 && cTop     < r.length) && parseBoolFlexible(safe(r, cTop));
                boolean titular = (cTitular >= 0 && cTitular < r.length) && parseBoolFlexible(safe(r, cTitular));

                if (DEBUG) {
                    System.out.println("[CSV] Lido jogador: " + nome +
                        " | estrela=" + estrela +
                        " | top=" + top +
                        " | titular=" + titular);
                }

                setBoolFlexible(j, FIELD_J_ESTRELA, estrela);
                setBoolFlexible(j, FIELD_J_TOP,     top);
                if (FIELD_J_TITULAR != null) setTitular(j, titular);

                System.out.println("[DEBUG] Lido jogador: " + nomeJogador
                + " | idade=" + safe(r, cIdade)
                + " | nac=" + safe(r, cNac)
                + " | pos=" + safe(r, cPos)
                + " | estrela=" + estrela
                + " | top=" + top
                + " | titular=" + titular);

                // adiciona e respeita o limite de 30
                nova.add(j);
                if (nova.size() >= MAX_JOGADORES_IMPORT) break;

                if (DEBUG) System.out.println("[ADD] Jogador adicionado: " + nome);

            }

            if (DEBUG) {
                int ctStar=0, ctTop=0, ctTit=0;
                for (Object jj : nova) {
                    try {
                        if (FIELD_REF_ESTRELA!=null) {
                            ctStar += toInt(FIELD_REF_ESTRELA.get(jj)) != 0 ? 1 : 0;
                        }
                        if (FIELD_REF_TOP!=null) {
                            ctTop  += toInt(FIELD_REF_TOP.get(jj)) != 0 ? 1 : 0;
                        }
                        if (FIELD_REF_TITULAR!=null) {
                            ctTit  += toInt(FIELD_REF_TITULAR.get(jj)) != 0 ? 1 : 0;
                        }
                    } catch (Exception ignore) {}
                }
                System.out.println(String.format("[DEBUG] Totais importados — estrela=%d, top=%d, titular=%d (de %d)",
                        ctStar, ctTop, ctTit, nova.size()));
        }

        // ===== FAIL-SAFE =====
        if (nova.isEmpty()) {
            if (DEBUG) System.out.println("[WARN] CSV não gerou jogadores válidos; mantendo elenco original do BAN.");
            // NÃO substitui a lista
        } else {
            set(clube, FIELD_CLUBE_LISTA, nova);
        }
    }

    // ===================== DETECÇÕES / REFLEXÃO =====================

    // ===== Descoberta dos campos booleanos (estrela/top/titular) =====
    public static Field FIELD_REF_ESTRELA = null;
    public static Field FIELD_REF_TOP     = null;
    public static Field FIELD_REF_TITULAR = null;

    /** Descobre dinamicamente quais campos do jogador são Estrela, Top e Titular.
 *  Funciona mesmo que o .ban use letras diferentes para cada atributo. */
    public static void detectBoolFields(List<?> jogadores) {
    if (jogadores == null || jogadores.isEmpty()) return;

    Object sample = jogadores.get(0);
    Class<?> jc = sample.getClass();

    // ----------------- localizar Estrela e Top por nome clássico -----------------
    try {
        // muitos dumps usam 'b' para estrela e 'j' para top
        Field fb = jc.getDeclaredField("b");
        fb.setAccessible(true);
        if (fb.getType() == boolean.class || fb.getType() == Boolean.class) FIELD_J_ESTRELA = "b";
    } catch (Throwable ignore) {}
    try {
        Field fj = jc.getDeclaredField("j");
        fj.setAccessible(true);
        if (fj.getType() == boolean.class || fj.getType() == Boolean.class) FIELD_J_TOP = "j";
    } catch (Throwable ignore) {}

    // ----------------- localizar Titular de forma ADAPTATIVA -----------------
    // 1) candidatos óbvios por nome
    String[] nameCandidates = TITULAR_CANDIDATES; // { "tid","t","f","sid","aid" }
    Field titularByName = null;
    for (String nm : nameCandidates) {
        try {
            Field f = jc.getDeclaredField(nm);
            f.setAccessible(true);
            Class<?> t = f.getType();
            if (t == boolean.class || t == Boolean.class ||
                t == int.class || t == Integer.class ||
                t == short.class || t == Short.class ||
                t == byte.class || t == Byte.class ||
                t == long.class || t == Long.class) {
                titularByName = f;
                break;
            }
        } catch (Throwable ignore) {}
    }

    // 2) varredura completa em busca do "campo mais booleano" que não é idade/pos/nac/… nem b/j
    Field titularAdaptive = null;
    double bestScore = -1;

    Field[] fs = jc.getDeclaredFields();
    for (Field f : fs) {
        try {
            if ((f.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0) continue;
            f.setAccessible(true);
            String nm = f.getName();

            // não confundir com campos sabidos
            if (nm.equals(FIELD_J_NOME) || nm.equals(FIELD_J_IDADE) || nm.equals(FIELD_J_NAC) ||
                nm.equals(FIELD_J_POS)  || nm.equals(FIELD_J_CAR1) || nm.equals(FIELD_J_CAR2) ||
                nm.equals(FIELD_J_LADO) || nm.equals("b")           || nm.equals("j"))
                continue;

            Class<?> t = f.getType();
            boolean numericLike = (t.isPrimitive() && t != char.class && t != float.class && t != double.class) ||
                                  Number.class.isAssignableFrom(t) ||
                                  t == boolean.class || t == Boolean.class;
            if (!numericLike) continue;

            int ones = 0, zeros = 0;
            int distinct = 0;
            java.util.Set<Integer> seen = new java.util.HashSet<>();

            for (Object j : jogadores) {
                Object v = f.get(j);
                int iv = toInt(v);                 // qualquer não-zero vira 1
                int bit = (iv != 0) ? 1 : 0;
                if (bit == 1) ones++; else zeros++;
                if (seen.add(iv)) distinct++;
            }

            // descartes: constante, todos 0 ou todos 1
            if (ones == 0 || zeros == 0) continue;

            // “quão bom” é para titular?
            // - melhor quando tem poucos valores distintos (0/1)
            // - preferir tipos booleanos
            // - bônus se o nome está na lista de candidatos
            double score = 0.0;
            score += (distinct <= 2) ? 2.0 : 0.5;
            score += (t == boolean.class || t == Boolean.class) ? 1.0 : 0.0;
            for (String nmC : nameCandidates) if (nm.equals(nmC)) { score += 2.0; break; }
            // distribuição razoável (nem raríssimo, nem todo mundo)
            double ratio = ones / (double)(ones + zeros);
            if (ratio >= 0.05 && ratio <= 0.95) score += 1.0;

            if (score > bestScore) { bestScore = score; titularAdaptive = f; }
        } catch (Throwable ignore) {}
    }

    // prioridade: por nome conhecido; senão, melhor adaptativo
    // -------- escolher o melhor campo (não priorizar cegamente o “nome bonito”) --------
    Field titularField = null;
    double scoreName  = (titularByName   != null) ? titularScore(titularByName, jogadores)   : -1.0;
    double scoreAdapt = (titularAdaptive != null) ? titularScore(titularAdaptive, jogadores) : -1.0;

    // se o adaptativo for claramente melhor, preferir ele
    if (scoreAdapt > scoreName + 0.2) {
        titularField = titularAdaptive;
    } else if (scoreName >= 0) {
        titularField = titularByName;
    } else {
        titularField = titularAdaptive;
    }

    if (titularField != null) {
        FIELD_J_TITULAR = titularField.getName();
        int[] cz = titularCounts(titularField, jogadores); // [zeros, ones]
        if (DEBUG) {
            System.out.println("[TIT ] campo=" + FIELD_J_TITULAR +
                " (" + titularField.getType().getSimpleName() + ") zeros=" + cz[0] + " ones=" + cz[1] +
                " score=" + String.format(java.util.Locale.ROOT, "%.2f",
                titularScore(titularField, jogadores)));
        }
    } else {
        FIELD_J_TITULAR = null;
        if (DEBUG) System.out.println("[TIT ] campo não encontrado");
    }


    // logs de estrela/top (útil no export também)
    if (DEBUG) {
        if (FIELD_J_ESTRELA != null) {
            try {
                Field f = jc.getDeclaredField(FIELD_J_ESTRELA);
                f.setAccessible(true);
                int ones=0,zeros=0;
                for (Object j : jogadores) { if (toInt(f.get(j))!=0) ones++; else zeros++; }
                System.out.println("[STAR] campo=" + FIELD_J_ESTRELA + " ("+f.getType().getSimpleName()+")");
            } catch (Throwable ignore) {}
        }
        if (FIELD_J_TOP != null) {
            try {
                Field f = jc.getDeclaredField(FIELD_J_TOP);
                f.setAccessible(true);
                int ones=0,zeros=0;
                for (Object j : jogadores) { if (toInt(f.get(j))!=0) ones++; else zeros++; }
                System.out.println("[TOP ] campo=" + FIELD_J_TOP + " ("+f.getType().getSimpleName()+")");
            } catch (Throwable ignore) {}
        }
    }
}

    public static String pickBooleanField(Class<?> jc, List<String> prefs) {
        for (String p : prefs) {
            Field f = findField(jc, p);
            if (f == null) continue;
            Class<?> t = f.getType();
            if (t == boolean.class || t == Boolean.class || t == int.class || t == Integer.class) return p;
        }
        // fallback: varre por boolean/int curto
        for (Field f : jc.getDeclaredFields()) {
            Class<?> t = f.getType();
            if (t==boolean.class||t==Boolean.class||t==int.class||t==Integer.class) return f.getName();
        }
        return null;
    }

    public static String pickTitularField(List<Object> jogadores, List<String> prefs) throws Exception {
        Class<?> jc = jogadores.get(0).getClass();
        // tenta preferidos
        for (String p : prefs) {
            Field f = findField(jc, p);
            if (f == null) continue;
            int ones = 0, zeros = 0;
            for (Object j : jogadores) {
                Object v = get(j, p);
                if (v == null) continue;
                int vi = (v instanceof Boolean) ? (((Boolean)v)?1:0) : toInt(v);
                if (vi == 0) zeros++; else ones++;
            }
            if (DEBUG) System.out.println("[INFO] Candidato titular '"+p+"': zeros="+zeros+" ones="+ones);
            if (ones > 0) return p;
        }
        // fallback: nenhuma coluna com 1s -> provavelmente todos 0 (sem titulares)
        return null;
    }

    public static Field findField(Class<?> c, String name) {
        Class<?> x = c;
        while (x != null) {
            try {
                Field f = x.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignore) {}
            x = x.getSuperclass();
        }
        return null;
    }

    public static Object get(Object o, String field) throws Exception {
        Field f = findField(o.getClass(), field);
        return (f==null) ? null : f.get(o);
    }
    public static int getInt(Object o, String field) throws Exception {
        Object v = get(o, field);
        return toInt(v);
    }
    public static boolean getBool(Object o, String field) throws Exception {
        Object v = get(o, field);
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean)v;
        return toInt(v) != 0;
    }
    public static void set(Object o, String field, Object value) throws Exception {
        Field f = findField(o.getClass(), field);
        if (f == null) return;
        if (f.getType()==int.class && value instanceof Integer) { f.setInt(o,(Integer)value); return; }
        if (f.getType()==boolean.class && value instanceof Boolean){ f.setBoolean(o,(Boolean)value); return; }
        f.set(o, value);
    }

    /** Lê “Titular/Colete” do objeto do jogador, tolerando boolean e números != 0. */
    public static boolean getTitular(Object j) throws Exception {
    if (FIELD_J_TITULAR == null) return false;
    Field f = j.getClass().getDeclaredField(FIELD_J_TITULAR);
    f.setAccessible(true);
    return toInt(f.get(j)) != 0;   // qualquer não-zero é true
    }

    public static Object detachPlayerByName(List<Object> lista, String nome) throws Exception {
        for (int i=0;i<lista.size();i++) {
            Object j = lista.get(i);
            String n = String.valueOf(get(j, FIELD_J_NOME));
            if (equalsName(n, nome)) { lista.remove(i); return j; }
        }
        return null;
    }

    // ===================== IO BAN =====================

    public static Object lerBan(Path p) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
            Object o = in.readObject();
            // tenta descobrir lista para detectar campos depois
            return o;
        }
    }
    public static Object safeReadBan(Path p) {
        try { return lerBan(p); } catch (Exception e) { if (DEBUG) e.printStackTrace(); return null; }
    }

    public static void salvarBan(Object o, Path out) throws Exception {
        Files.createDirectories(out.getParent());
        try (ObjectOutputStream outS = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(out)))) {
            outS.writeObject(o);
        }
    }

    // ===================== CSV UTILS =====================

    public static List<String[]> lerCsv(Path csv) throws IOException {
        Charset cs = detectCsvCharset(csv);
        List<String> lines = Files.readAllLines(csv, cs);
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split(";", -1);
            rows.add(parts);
        }
        return rows;
    }

    public static void escreverCsv(Path out, List<String[]> linhas, boolean bom) throws IOException {
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            if (bom) w.write('\uFEFF');
            for (String[] linha : linhas) {
                w.write(String.join(";", linha));
                w.newLine();
            }
        }
    }

    public static Charset detectCsvCharset(Path csv) {
        // UTF-8 BOM?
        try (InputStream in = Files.newInputStream(csv)) {
            byte[] b = new byte[3];
            int n = in.read(b);
            if (n==3 && (b[0]&0xFF)==0xEF && (b[1]&0xFF)==0xBB && (b[2]&0xFF)==0xBF) return StandardCharsets.UTF_8;
        } catch (IOException ignore) {}
        return StandardCharsets.ISO_8859_1;
    }

    // ===================== DIAGNÓSTICO =====================

    public static void diagClube(Object clube, boolean sampleJog) throws Exception {
        System.out.println("Classe clube: " + clube.getClass().getName());
        System.out.println("nome(" + FIELD_CLUBE_NOME + "): " + get(clube, FIELD_CLUBE_NOME));
        System.out.println("pais(" + FIELD_CLUBE_PAIS + "): " + getInt(clube, FIELD_CLUBE_PAIS));
        System.out.println("nivel(" + FIELD_CLUBE_NIVEL + "): " + getInt(clube, FIELD_CLUBE_NIVEL));
        System.out.println("estadio(" + FIELD_CLUBE_ESTADIO + "): " + get(clube, FIELD_CLUBE_ESTADIO));
        System.out.println("capacidade(" + FIELD_CLUBE_CAP + "): " + getInt(clube, FIELD_CLUBE_CAP));
        System.out.println("treinador(" + FIELD_CLUBE_TREINADOR + "): " + get(clube, FIELD_CLUBE_TREINADOR));

        List<Object> js = (List)get(clube, FIELD_CLUBE_LISTA);
        System.out.println("campo " + FIELD_CLUBE_LISTA + " = " + (js==null?null:js.getClass().getName()));
        System.out.println("qtd jogadores: " + (js==null?0:js.size()));
        if (sampleJog && js!=null && !js.isEmpty()) {
            Object j0 = js.get(0);
            System.out.println("classe jogador: " + j0.getClass().getName());
            System.out.println("j0.nome("+FIELD_J_NOME+"): " + get(j0, FIELD_J_NOME));
            System.out.println("j0.idade("+FIELD_J_IDADE+"): " + getInt(j0, FIELD_J_IDADE));
            System.out.println("j0.pos("+FIELD_J_POS+"): " + getInt(j0, FIELD_J_POS));
            System.out.println("j0.car1("+FIELD_J_CAR1+"): " + getInt(j0, FIELD_J_CAR1));
            System.out.println("j0.car2("+FIELD_J_CAR2+"): " + getInt(j0, FIELD_J_CAR2));
            System.out.println("j0.lado("+FIELD_J_LADO+"): " + getInt(j0, FIELD_J_LADO));
            System.out.println("j0.nac("+FIELD_J_NAC+"): " + getInt(j0, FIELD_J_NAC));
            System.out.println("j0.estrela("+FIELD_J_ESTRELA+"): " + getBool(j0, FIELD_J_ESTRELA));
            System.out.println("j0.top("+FIELD_J_TOP+"): " + getBool(j0, FIELD_J_TOP));
            if (FIELD_J_TITULAR!=null) System.out.println("j0.titular("+FIELD_J_TITULAR+"): " + getTitular(j0));
        }
        System.out.println("===================");
    }

    // ===================== HELPERS =====================

    /** Procura a linha de cabeçalho dos jogadores a partir de 'start'.
     *  Critério: linha que contenha ao menos 2 entre: Nome, Idade, Nacionalidade/País, Posição. */
    public static int findHeaderIdx(List<String[]> rows, int start) {
        for (int k = Math.max(0, start); k < rows.size(); k++) {
            String[] r = rows.get(k);
            if (r == null || r.length == 0) continue;

            // ignore títulos soltos
            if (r.length == 1) {
                String t = normKey(safe(r[0]));
                if (t.equals("clube") || t.equals("jogadores")) continue;
            }

            int hits = 0;
            for (String cell : r) {
                String n = normKey(cell);
                if (n.equals("nome")) hits++;
                else if (n.equals("idade")) hits++;
                else if (n.equals("nacionalidade") || n.equals("pais")) hits++;
                else if (n.equals("posicao")) hits++;
            }
            if (hits >= 2) return k;  // achamos um header plausível
        }
        return -1;
    }

    // Converte array para minúsculas (tolerante a null)
    public static String[] toLower(String[] a) {
        if (a == null) return new String[0];
        String[] b = new String[a.length];
        for (int i = 0; i < a.length; i++) b[i] = (a[i] == null ? null : a[i].toLowerCase(Locale.ROOT));
        return b;
    }

    public static String nextArg(List<String> a, String flag) {
        int i = a.indexOf(flag);
        if (i < 0 || i + 1 >= a.size()) throw new IllegalArgumentException("Faltando argumento de " + flag);
        return a.get(i + 1);
    }

    public static String replaceExt(String name, String newExt) {
        int i = name.lastIndexOf('.');
        if (i < 0) return name + newExt;
        return name.substring(0, i) + newExt;
    }

    // --- normalização básica/BOM/invisíveis ---
    public static String stripBom(String s) {
        if (s == null) return "";
        // BOM (U+FEFF) + LRM/RLM (U+200E/U+200F) + zero width comuns
        return s
            .replace("\uFEFF","")
            .replace("\u200E","")
            .replace("\u200F","")
            .replace("\u200B","")
            .replace("\u200C","")
            .replace("\u200D","")
            .trim();
    }

    /** Lê célula do CSV com segurança (tolera null e índice fora). */
    public static String safe(String[] r, int idx) {
        if (r == null || idx < 0 || idx >= r.length) return "";
        String v = r[idx];
        return stripBom(v == null ? "" : v);
    }

    /** Retorna "" se nulo; caso contrário, trim + stripBom. */
    public static String safe(String s) {
        return stripBom(s == null ? "" : s);
    }

    /** Inteiro tolerante: vazio/nulo/formatado vira 0 ao invés de erro. */
    public static int parseIntSafe(String s) {
        s = safe(s);
        if (s.isEmpty()) return 0;
        try {
            // remove separadores comuns de milhar
            s = s.replace(".", "").replace(" ", "");
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Normaliza removendo acentos e não-letras/números, em minúsculas. */
    public static String normKey(String s) {
        s = safe(s); // já tira BOM/zero-width/trim
        if (s.isEmpty()) return "";
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", ""); // remove diacríticos
        t = t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "").trim();
        return t;
    }

    public static boolean eqIgnoreAccents(String a, String b) { return normKey(a).equals(normKey(b)); }

    /** Boolean flexível: aceita várias formas afirmativas/negativas. */
    public static boolean parseBoolFlexible(String s) {
        String n = normKey(s);
        if (n.isEmpty()) return false;

        // afirmativos
        if (n.equals("1") || n.equals("true") || n.equals("sim") || n.equals("s")
                || n.equals("y") || n.equals("yes") || n.equals("on")
                || n.equals("*") || n.equals("x") || n.equals("✓")
                // rótulos usuais de colunas
                || n.equals("estrela") || n.equals("top") || n.equals("topmundial") || n.equals("tm")
                || n.equals("titular") || n.equals("colete")) {
            return true;
        }

        // negativos explícitos
        if (n.equals("0") || n.equals("false") || n.equals("nao") || n.equals("n") || n.equals("off")) {
            return false;
        }

        return false;
    }


    /** true se parecer um inteiro (apenas dígitos, com/sem espaços). */
    public static boolean isIntLike(String s) {
        try { Integer.parseInt(safe(s)); return true; } catch (Exception e) { return false; }
    }

    public static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    public static String joinNonEmpty(String a, String b) {
        if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) return "";
        if (a == null || a.isEmpty()) return b;
        if (b == null || b.isEmpty()) return a;
        return a + ", " + b;
    }

    public static boolean equalsName(String a, String b) { return eqIgnoreAccents(a, b); }

    // ---------- Helpers extras p/ flags/clonagem de jogador ----------

    /** Mede a “qualidade” de um campo para ser Titular:
     *  - descarta se for constante (todos 0 ou todos !=0)
     *  - favorece quando só há 0/1 (poucos distintos)
     *  - favorece boolean
     *  - favorece distribuição razoável (nem raríssima)
     */
    public static double titularScore(Field f, List<?> jogadores) {
        try {
            f.setAccessible(true);
            int ones = 0, zeros = 0;
            java.util.Set<Integer> distinct = new java.util.HashSet<>();
            for (Object j : jogadores) {
                int iv = toInt(f.get(j));
                distinct.add(iv);
                if (iv != 0) ones++; else zeros++;
            }
            if (ones == 0 || zeros == 0) return -1.0;           // constante -> ruim
            double score = 0.0;
            score += (distinct.size() <= 2) ? 2.0 : 0.5;        // 0/1 é melhor
            if (f.getType() == boolean.class || f.getType() == Boolean.class) score += 1.0;
            double ratio = ones / (double) (ones + zeros);
            if (ratio >= 0.05 && ratio <= 0.95) score += 1.0;   // distribuição plausível
            return score;
        } catch (Throwable ignore) {
            return -1.0;
        }
    }

    public static int[] titularCounts(Field f, List<?> jogadores) {
        try {
            f.setAccessible(true);
            int ones = 0, zeros = 0;
            for (Object j : jogadores) {
                int iv = toInt(f.get(j));
                if (iv != 0) ones++; else zeros++;
            }
            return new int[]{zeros, ones};
        } catch (Throwable ignore) {
            return new int[]{0, 0};
        }
    }


// ==== TITULAR (para export) ====

// modo de codificação do campo titular
public enum TitMode { NONE, BOOLEAN, POINTER }

@SuppressWarnings("unchecked")
public static TitMode detectTitularModeForExport(List<Object> jogadores) {
    if (FIELD_J_TITULAR == null || jogadores == null || jogadores.isEmpty()) return TitMode.NONE;

    int positives = 0, eqIndex = 0, ones = 0, nonZeroDistinct = 0;
    java.util.Set<Integer> seen = new java.util.HashSet<>();

    for (int i = 0; i < jogadores.size(); i++) {
        Object j = jogadores.get(i);
        Object v;
        try {
            v = get(j, FIELD_J_TITULAR);   // get lança Exception → tratar aqui
        } catch (Exception e) {
            continue; // se não conseguir ler, ignora este jogador na heurística
        }
        if (v == null) continue;

        if (v instanceof Boolean) {
            if ((Boolean) v) positives++;
            continue;
        }

        int n = toInt(v);
        if (n > 0) {
            positives++;
            if (n == 1) ones++;
            if (n == (i + 1)) eqIndex++;
            if (seen.add(n)) nonZeroDistinct++;
        }
    }

    if (eqIndex == 1) return TitMode.POINTER;
    if (positives > 1 && nonZeroDistinct == 1) return TitMode.BOOLEAN;
    if (nonZeroDistinct > 1) return TitMode.POINTER;
    if (positives == 1) return TitMode.POINTER;
    if (positives > 0) return TitMode.BOOLEAN;
    return TitMode.NONE;
}

public static boolean isTitularForExport(int idx, Object j, TitMode mode) {
    if (FIELD_J_TITULAR == null || j == null) return false;

    Object v;
    try {
        v = get(j, FIELD_J_TITULAR);       // get lança Exception → tratar aqui
    } catch (Exception e) {
        return false;
    }
    if (v == null) return false;

    if (v instanceof Boolean) return (Boolean) v;

    int n = toInt(v);
    if (mode == TitMode.POINTER) {
        return n == (idx + 1);             // 1-based
    } else if (mode == TitMode.BOOLEAN) {
        return n > 0;
    }
    return false;
}


    /** Retorna o Field (procura na classe e superclasses). */
    public static java.lang.reflect.Field getFieldRecursive(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** Copia TODOS os campos, inclusive privados, de src -> dst (mesma classe). */
    public static void copyAllFields(Object src, Object dst) throws Exception {
        Class<?> c = src.getClass();
        while (c != null) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(src);
                f.set(dst, v);
            }
            c = c.getSuperclass();
        }
    }

/** Define campo booleano em j (aceita campo boolean ou int 0/1). */
public static void setBoolFlexible(Object j, String fieldName, boolean val) throws Exception {
    if (fieldName == null) return;
    Field f = findField(j.getClass(), fieldName);
    if (f == null) return;
    f.setAccessible(true);

    Class<?> t = f.getType();
    if (t == boolean.class || t == Boolean.class) {
        f.set(j, val);
    } else if (t == int.class || t == Integer.class) {
        f.set(j, val ? 1 : 0);
    } else if (t == byte.class || t == Byte.class) {
        f.set(j, (byte)(val ? 1 : 0));
    } else if (t == short.class || t == Short.class) {
        f.set(j, (short)(val ? 1 : 0));
    } else {
        // fallback: tenta via string
        f.set(j, val ? 1 : 0);
    }
}

/** Define “Titular/Colete” no objeto do jogador. Aceita boolean/int/short/byte/long. */
public static void setTitular(Object j, boolean value) throws Exception {
    if (FIELD_J_TITULAR == null) return;
    Field f = j.getClass().getDeclaredField(FIELD_J_TITULAR);
    setAsInt(j, f, value);         // escreve 1/0 em numéricos, ou booleano direto
}

    // ===================== MAPEAMENTOS =====================

    static {
        // POSIÇÕES
        putPos(0,"Goleiro","GOL","GK");
        putPos(1,"Lateral","LAT","Fullback");
        putPos(2,"Zagueiro","ZAG","DEF","CB");
        putPos(3,"Meia","MEI","Midfielder","MF");
        putPos(4,"Atacante","ATA","FW","ST","Avançado");

        // LADOS
        putLado(0,"Direito","Dir","D","Right","R");
        putLado(1,"Esquerdo","Esq","E","Left","L");

        // CARACTERÍSTICAS (com abreviações)
        putCarac(0,"Colocação","Col");
        putCarac(1,"Defesa Penalty","DPe","Def Pênalti","Def Penalti");
        putCarac(2,"Reflexo","Ref");
        putCarac(3,"Saída gol","SGo","Saida gol");
        putCarac(4,"Armação","Arm");
        putCarac(5,"Cabeceio","Cab","Cabeceiro");
        putCarac(6,"Cruzamento","Cru");
        putCarac(7,"Desarme","Des");
        putCarac(8,"Drible","Dri");
        putCarac(9,"Finalização","Fin","Finalizacao");
        putCarac(10,"Marcação","Mar","Marcacao");
        putCarac(11,"Passe","Pas");
        putCarac(12,"Resistência","Res","Resistencia");
        putCarac(13,"Velocidade","Vel");

        // Países – use addPais(alias, id, nomeCanônico):
        // ====== PAISES (lista completa) ======
        addPais("Afeganistão", 0);
        addPais("África do Sul", 1);
        addPais("Albânia", 2);
        addPais("Alemanha", 3);
        addPais("Andorra", 4);
        addPais("Angola", 5);
        addPais("Anguilla", 6);
        addPais("Antígua", 7);
        addPais("Curaçao", 8);
        addPais("Arábia Saudita", 9);
        addPais("Argélia", 10);
        addPais("Argentina", 11);
        addPais("Armênia", 12);
        addPais("Aruba", 13);
        addPais("Austrália", 14);
        addPais("Áustria", 15);
        addPais("Azerbaijão", 16);
        addPais("Bahamas", 17);
        addPais("Bahrain", 18);
        addPais("Bangladesh", 19);
        addPais("Barbados", 20);
        addPais("Bélgica", 21);
        addPais("Belize", 22);
        addPais("Benin", 23);
        addPais("Bermudas", 24);
        addPais("Belarus", 25);
        addPais("Bolívia", 26);
        addPais("Bósnia", 27);
        addPais("Botsuana", 28);
        addPais("Brasil", 29);
        addPais("Brunei", 30);
        addPais("Bulgária", 31);
        addPais("Burkina Faso", 32);
        addPais("Burundi", 33);
        addPais("Butão", 34);
        addPais("Cabo Verde", 35);
        addPais("Camarões", 36);
        addPais("Camboja", 37);
        addPais("Canadá", 38);
        addPais("Catar", 39);
        addPais("Cazaquistão", 40);
        addPais("Chade", 41);
        addPais("Chile", 42);
        addPais("China", 43);
        addPais("Chipre", 44);
        addPais("Timor-Leste", 45);
        addPais("Colômbia", 46);
        addPais("Congo", 47);
        addPais("Coreia do Norte", 48);
        addPais("Coreia do Sul", 49);
        addPais("Costa do Marfim", 50);
        addPais("Costa Rica", 51);
        addPais("Croácia", 52);
        addPais("Cuba", 53);
        addPais("Dinamarca", 54);
        addPais("Djibuti", 55);
        addPais("Dominica", 56);
        addPais("Egito", 57);
        addPais("El Salvador", 58);
        addPais("Emirados Árabes", 59);
        addPais("Equador", 60);
        addPais("Eritréia", 61);
        addPais("Escócia", 62);
        addPais("Eslováquia", 63);
        addPais("Eslovênia", 64);
        addPais("Espanha", 65);
        addPais("Estônia", 66);
        addPais("Etiópia", 67);
        addPais("EUA", 68);
        addPais("Fiji", 69);
        addPais("Finlândia", 70);
        addPais("Filipinas", 71);
        addPais("França", 72);
        addPais("Gabão", 73);
        addPais("Gâmbia", 74);
        addPais("Gana", 75);
        addPais("Georgia", 76);
        addPais("Granada", 77);
        addPais("Grécia", 78);
        addPais("Guatemala", 79);
        addPais("Guiana", 80);
        addPais("Guiné", 81);
        addPais("Guiné-Bissau", 82);
        addPais("Guiné Equatorial", 83);
        addPais("Haiti", 84);
        addPais("Holanda", 85);
        addPais("Honduras", 86);
        addPais("Hong Kong", 87);
        addPais("Hungria", 88);
        addPais("Iêmen", 89);
        addPais("Ilhas Cayman", 90);
        addPais("Ilhas Cook", 91);
        addPais("Ilhas Faroe", 92);
        addPais("Ilhas Salomão", 93);
        addPais("Ilhas Virgens Britânicas", 94);
        addPais("Índia", 95);
        addPais("Indonésia", 96);
        addPais("Inglaterra", 97);
        addPais("Irã", 98);
        addPais("Iraque", 99);
        addPais("Irlanda", 100);
        addPais("Irlanda do Norte", 101);
        addPais("Islândia", 102);
        addPais("Israel", 103);
        addPais("Itália", 104);
        addPais("Montenegro", 105);
        addPais("Jamaica", 106);
        addPais("Japão", 107);
        addPais("Jordânia", 108);
        addPais("Quênia", 109);
        addPais("Kosovo", 110);
        addPais("Kuwait", 111);
        addPais("Laos", 112);
        addPais("Lesoto", 113);
        addPais("Letônia", 114);
        addPais("Líbano", 115);
        addPais("Líbia", 116);
        addPais("Libéria", 117);
        addPais("Liechtenstein", 118);
        addPais("Lituânia", 119);
        addPais("Luxemburgo", 120);
        addPais("Macau", 121);
        addPais("Macedônia do Norte", 122);
        addPais("Madagascar", 123);
        addPais("Malásia", 124);
        addPais("Malawi", 125);
        addPais("Maldivas", 126);
        addPais("Mali", 127);
        addPais("Malta", 128);
        addPais("Marrocos", 129);
        addPais("Mauritânia", 130);
        addPais("México", 131);
        addPais("Mianmar", 132);
        addPais("Moçambique", 133);
        addPais("Moldávia", 134);
        addPais("Mônaco", 135);
        addPais("Mongólia", 136);
        addPais("Namíbia", 137);
        addPais("Nauru", 206);
        addPais("Nepal", 138);
        addPais("Nicarágua", 139);
        addPais("Níger", 140);
        addPais("Nigéria", 141);
        addPais("Noruega", 142);
        addPais("Nova Zelândia", 143);
        addPais("Omã", 144);
        addPais("País de Gales", 145);
        addPais("Palestina", 146);
        addPais("Panamá", 147);
        addPais("Papua Nova Guiné", 148);
        addPais("Paquistão", 149);
        addPais("Paraguai", 150);
        addPais("Peru", 151);
        addPais("Polônia", 152);
        addPais("Porto Rico", 153);
        addPais("Portugal", 154);
        addPais("Quirguistão", 155);
        addPais("Rep. Centro-Africana", 156);
        addPais("Rep. Dem. Congo", 157);
        addPais("Rep. Dominicana", 158);
        addPais("República Tcheca", 159);
        addPais("Romênia", 160);
        addPais("Ruanda", 161);
        addPais("Rússia", 162);
        addPais("Samoa", 163);
        addPais("San Marino", 164);
        addPais("Santa Lúcia", 165);
        addPais("São Cristóvão e Névis", 166);
        addPais("São Tomé e Príncipe", 167);
        addPais("São Vicente e Granadinas", 168);
        addPais("Senegal", 169);
        addPais("Serra Leoa", 170);
        addPais("Sérvia", 171);
        addPais("Seychelles", 172);
        addPais("Singapura", 173);
        addPais("Síria", 174);
        addPais("Somália", 175);
        addPais("Sri Lanka", 176);
        addPais("Essuatíni", 177);
        addPais("Sudão", 178);
        addPais("Suécia", 179);
        addPais("Suíça", 180);
        addPais("Suriname", 181);
        addPais("Tadjiquistão", 182);
        addPais("Tailândia", 183);
        addPais("Taiti", 184);
        addPais("Taipé Chinês", 185);
        addPais("Tanzânia", 186);
        addPais("Togo", 187);
        addPais("Tonga", 188);
        addPais("Trinidad e Tobago", 189);
        addPais("Tunísia", 190);
        addPais("Turcomenistão", 191);
        addPais("Turquia", 192);
        addPais("Ucrânia", 193);
        addPais("Uganda", 194);
        addPais("Uruguai", 195);
        addPais("Uzbequistão", 196);
        addPais("Vanuatu", 197);
        addPais("Venezuela", 198);
        addPais("Vietnã", 199);
        addPais("Zâmbia", 200);
        addPais("Zimbábue", 201);

        // Territórios e códigos “200+” da sua lista:
        addPais("Comores", 202);
        addPais("Micronésia", 203);
        addPais("Ilhas Marshall", 204);
        addPais("Maurícia", 205);
        addPais("Nauru", 206);        // (já acima, repete ok)
        addPais("Palau", 207);
        addPais("Kiribati", 208);
        addPais("Sudão do Sul", 209);
        addPais("Tuvalu", 210);
        addPais("Ilhas Virgens Americanas", 211);
        addPais("Montserrat", 212);
        addPais("Ilhas Turks e Caicos", 213);
        addPais("Samoa Americana", 214);
        addPais("Nova Caledônia", 215);
        addPais("Gibraltar", 216);
        addPais("Guadalupe", 217);
        addPais("Guam", 218);
        addPais("Martinica", 219);
        addPais("Guiana Francesa", 220);
        addPais("Bonaire", 221);
        addPais("São Martinho França", 222);
        addPais("São Martinho Holanda", 223);
    }

    public static void putPos(int id, String... nomes) {
        for (String n : nomes) POSICOES.put(normKey(n), id);
        POSICOES_REV.put(id, nomes[0]);
    }
    public static void putLado(int id, String... nomes) {
        for (String n : nomes) LADOS.put(normKey(n), id);
        LADOS_REV.put(id, nomes[0]);
    }
    public static void putCarac(int id, String... nomes) {
        for (String n : nomes) CARACS.put(normKey(n), id);
        CARACS_REV.put(id, nomes[0]);
    }

    public static void addPais(String nomePrincipal, int id, String... aliases) {
        // nome canônico
        PAISES.put(normKey(nomePrincipal), id);
        PAISES_REV.put(idKey(id), nomePrincipal);
        for (String a : aliases) PAISES.put(normKey(a), id);
    }

    public static String idKey(int id) { return String.valueOf(id); }

    public static String posNome(int id) { return POSICOES_REV.getOrDefault(id, String.valueOf(id)); }
    public static int posNomeToId(String s) { return POSICOES.getOrDefault(normKey(s), 0); }

    public static String ladoNome(int id) { return LADOS_REV.getOrDefault(id, String.valueOf(id)); }
    public static int ladoNomeToId(String s) { return LADOS.getOrDefault(normKey(s), 0); }

    public static String caracNome(int id) { return CARACS_REV.getOrDefault(id, ""); }
    public static int caracNomeToId(String s) { return CARACS.getOrDefault(normKey(s), 0); }

    public static String paisIdToNome(int id) {
        String n = PAISES_REV.get(idKey(id));
        return (n==null) ? String.valueOf(id) : n;
    }
    public static int paisNomeToId(String s) {
        Integer v = PAISES.get(normKey(s));
        return v==null?0:v;
    }
}
