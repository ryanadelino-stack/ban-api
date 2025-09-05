package bf.tools;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.*;
import java.io.*;

@CrossOrigin(origins="*")
@RestController
@RequestMapping("/api")
public class BanController {

  // === 1) Exportar modelo (um .ban -> CSV) ===
  @PostMapping(value="/export-model", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> exportModel(
      @RequestPart("ban") MultipartFile banFile
  ) throws Exception {
    Path tmp = Files.createTempDirectory("banapi");
    Path banIn = tmp.resolve(Objects.requireNonNull(banFile.getOriginalFilename(), "file.ban"));
    Files.copy(banFile.getInputStream(), banIn, StandardCopyOption.REPLACE_EXISTING);

    Path outCsv = tmp.resolve(replaceExt(banIn.getFileName().toString(), ".csv"));
    // chama seu método: exportModeloSingle(banIn, outCsv)
    BanEditorReflect.exportModeloSingle(banIn, outCsv);

    byte[] bytes = Files.readAllBytes(outCsv);
    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+outCsv.getFileName()+"\"")
      .contentType(MediaType.parseMediaType("text/csv"))
      .body(bytes);
  }

  // === 2) Importar/aplicar CSV (ban + csv -> ban alterado) ===
  @PostMapping(value="/import-apply", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> importApply(
      @RequestPart("ban") MultipartFile banFile,
      @RequestPart("csv") MultipartFile csvFile,
      @RequestParam(value="debug", required=false, defaultValue="false") boolean debug
  ) throws Exception {
    Path tmp = Files.createTempDirectory("banapi");
    Path banIn = tmp.resolve(Objects.requireNonNull(banFile.getOriginalFilename(), "in.ban"));
    Files.copy(banFile.getInputStream(), banIn, StandardCopyOption.REPLACE_EXISTING);
    Path csvIn = tmp.resolve(Objects.requireNonNull(csvFile.getOriginalFilename(), "dados.csv"));
    Files.copy(csvFile.getInputStream(), csvIn, StandardCopyOption.REPLACE_EXISTING);

    Path banOut = tmp.resolve("saida.ban");
    // chama seu método principal: processSingle(banIn, csvIn, banOut) com DEBUG opcional
    boolean oldDebug = BanEditorReflect.DEBUG;
    BanEditorReflect.DEBUG = debug;
    try {
      BanEditorReflect.processSingle(banIn, csvIn, banOut);
    } finally {
      BanEditorReflect.DEBUG = oldDebug;
    }

    byte[] bytes = Files.readAllBytes(banOut);
    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+banOut.getFileName()+"\"")
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .body(bytes);
  }

  // === 3) Exportar jogadores em um único CSV (pasta zip com .ban -> CSV único) ===
  @PostMapping(value="/export-players", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> exportPlayers(
      @RequestPart("bansZip") MultipartFile bansZip
  ) throws Exception {
    Path tmp = Files.createTempDirectory("banapi");
    Path inDir = tmp.resolve("input");
    Files.createDirectories(inDir);
    unzipTo(bansZip.getInputStream(), inDir);

    Path outCsv = tmp.resolve("jogadores.csv");
    BanEditorReflect.exportPlayersBatch(inDir, outCsv);

    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jogadores.csv\"")
      .contentType(MediaType.parseMediaType("text/csv"))
      .body(Files.readAllBytes(outCsv));
  }

  // === 4) Transferências (CSV + pasta zip .ban -> zip de bans out) ===
  @PostMapping(value="/apply-transfers", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> applyTransfers(
      @RequestPart("transfers") MultipartFile transfers,
      @RequestPart("bansZip") MultipartFile bansZip
  ) throws Exception {
    Path tmp = Files.createTempDirectory("banapi");
    Path inDir = tmp.resolve("in");  Files.createDirectories(inDir);
    Path outDir= tmp.resolve("out"); Files.createDirectories(outDir);
    unzipTo(bansZip.getInputStream(), inDir);

    Path transfersCsv = tmp.resolve("transferencias.csv");
    Files.copy(transfers.getInputStream(), transfersCsv, StandardCopyOption.REPLACE_EXISTING);

    BanEditorReflect.applyTransfersNoCreate(transfersCsv, inDir, outDir);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    zipDir(outDir, baos);
    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bans_out.zip\"")
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .body(baos.toByteArray());
  }

  // ===== utilitários bem básicos (zip/unzip + replaceExt) =====
  private static void unzipTo(InputStream in, Path target) throws IOException {
    try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(in)) {
      java.util.zip.ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        Path p = target.resolve(e.getName()).normalize();
        if (e.isDirectory()) { Files.createDirectories(p); }
        else {
          Files.createDirectories(p.getParent());
          Files.copy(zis, p, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }
  private static void zipDir(Path dir, OutputStream out) throws IOException {
    try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(out)) {
      Files.walk(dir).filter(Files::isRegularFile).forEach(p -> {
        try {
          String entry = dir.relativize(p).toString().replace('\\','/');
          zos.putNextEntry(new java.util.zip.ZipEntry(entry));
          Files.copy(p, zos);
          zos.closeEntry();
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
      });
    }
  }
  private static String replaceExt(String name, String newExt) {
    int i = name.lastIndexOf('.');
    return (i<0) ? name + newExt : name.substring(0,i) + newExt;
  }
}
