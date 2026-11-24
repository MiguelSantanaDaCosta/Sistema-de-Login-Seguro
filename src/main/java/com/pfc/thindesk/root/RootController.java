package com.pfc.thindesk.root;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

/**
 * Painel root: navegar, editar, inserir e excluir QUALQUER documento dos bancos da aplicação
 * (thindesk e ms_chamados). Somente ROLE_ROOT (SecurityConfig + @PreAuthorize).
 */
@Controller
@RequestMapping("/root")
@PreAuthorize("hasRole('ROOT')")
public class RootController {

    private static final int POR_PAGINA = 20;
    private static final JsonWriterSettings JSON = JsonWriterSettings.builder()
            .outputMode(JsonMode.RELAXED).indent(true).build();

    public record Doc(String json, String filtro) {
    }

    @Autowired
    private MongoClient mongoClient;
    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping
    public String painel(@RequestParam(required = false) String db,
            @RequestParam(required = false) String col,
            @RequestParam(defaultValue = "0") int pagina,
            Model model) {
        Set<String> bancos = bancosPermitidos();
        String dbAtual = db != null && bancos.contains(db) ? db : bancos.iterator().next();
        MongoDatabase banco = mongoClient.getDatabase(dbAtual);

        List<String[]> colecoes = new ArrayList<>();
        for (String nome : banco.listCollectionNames()) {
            colecoes.add(new String[] { nome, String.valueOf(banco.getCollection(nome).estimatedDocumentCount()) });
        }
        colecoes.sort((a, b) -> a[0].compareTo(b[0]));

        List<Doc> docs = new ArrayList<>();
        long total = 0;
        if (col != null && !col.isBlank()) {
            var c = banco.getCollection(col);
            total = c.countDocuments();
            for (Document d : c.find().skip(Math.max(pagina, 0) * POR_PAGINA).limit(POR_PAGINA)) {
                docs.add(new Doc(d.toJson(JSON), new Document("_id", d.get("_id")).toJson()));
            }
        }

        model.addAttribute("bancos", bancos);
        model.addAttribute("db", dbAtual);
        model.addAttribute("col", col);
        model.addAttribute("colecoes", colecoes);
        model.addAttribute("docs", docs);
        model.addAttribute("pagina", pagina);
        model.addAttribute("total", total);
        model.addAttribute("porPagina", POR_PAGINA);
        model.addAttribute("content", "root/painel :: content");
        return "layout";
    }

    /** Insere (sem _id) ou substitui (com _id, upsert) um documento a partir de JSON. */
    @PostMapping("/doc/salvar")
    public String salvar(@RequestParam String db, @RequestParam String col, @RequestParam String json,
            RedirectAttributes redirect) {
        try {
            var c = banco(db).getCollection(nomeValido(col));
            Document d = Document.parse(json);
            if (d.containsKey("_id")) {
                c.replaceOne(Filters.eq("_id", d.get("_id")), d, new ReplaceOptions().upsert(true));
            } else {
                c.insertOne(d);
            }
            redirect.addFlashAttribute("sucesso", "Documento salvo.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", "Falha ao salvar: " + e.getMessage());
        }
        return voltar(db, col);
    }

    @PostMapping("/doc/excluir")
    public String excluir(@RequestParam String db, @RequestParam String col, @RequestParam String filtro,
            RedirectAttributes redirect) {
        try {
            Document f = Document.parse(filtro);
            if (f.size() != 1 || !f.containsKey("_id")) {
                throw new IllegalArgumentException("filtro inválido");
            }
            long n = banco(db).getCollection(nomeValido(col)).deleteOne(f).getDeletedCount();
            redirect.addFlashAttribute("sucesso", n + " documento(s) excluído(s).");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", "Falha ao excluir: " + e.getMessage());
        }
        return voltar(db, col);
    }

    @PostMapping("/colecao/excluir")
    public String excluirColecao(@RequestParam String db, @RequestParam String col, RedirectAttributes redirect) {
        try {
            banco(db).getCollection(nomeValido(col)).drop();
            redirect.addFlashAttribute("sucesso", "Coleção '" + col + "' removida.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", "Falha ao remover: " + e.getMessage());
        }
        return "redirect:/root?db=" + db;
    }

    // Só os bancos da própria aplicação (nunca admin/local/config do cluster)
    private Set<String> bancosPermitidos() {
        return new java.util.LinkedHashSet<>(List.of(mongoTemplate.getDb().getName(), "ms_chamados"));
    }

    private MongoDatabase banco(String nome) {
        if (!bancosPermitidos().contains(nome)) {
            throw new IllegalArgumentException("banco não permitido");
        }
        return mongoClient.getDatabase(nome);
    }

    private String nomeValido(String col) {
        if (col == null || !col.matches("[A-Za-z0-9_\\-.]{1,64}") || col.startsWith("system.")) {
            throw new IllegalArgumentException("coleção inválida");
        }
        return col;
    }

    private String voltar(String db, String col) {
        return "redirect:/root?db=" + db + "&col=" + col;
    }
}
