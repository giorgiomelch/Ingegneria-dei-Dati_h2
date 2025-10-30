package com.example.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Programma per indicizzare file .txt da una directory e per cercare nell'indice.
 */
public class App {

    private static final String INDEX_DIR = "/home/giorgiomelch/ID/homework2/index";
    private static final String DATA_DIR = "/home/giorgiomelch/ID/homework2/data";

    public static void main(String[] args) {
        try {
            Directory indexDirectory = FSDirectory.open(Paths.get(INDEX_DIR));
            createIndex(indexDirectory);
            search(indexDirectory);
            indexDirectory.close();

        } catch (IOException e) {
            System.err.println("Errore durante l'indicizzazione: " + e.getMessage());
            e.printStackTrace();
        } catch (ParseException e) {
            System.err.println("Errore durante il parsing della query: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static PerFieldAnalyzerWrapper getAnalyzer() {
        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        analyzerPerField.put("filename", new StandardAnalyzer());
        analyzerPerField.put("content", new ItalianAnalyzer()); 
        
        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
    }

    private static void createIndex(Directory indexDirectory) throws IOException {
        System.out.println("Inizio indicizzazione...");
        System.out.println("Directory indice: " + INDEX_DIR);
        System.out.println("Directory dati: " + DATA_DIR);

        PerFieldAnalyzerWrapper analyzer = getAnalyzer();

        // configura l'IndexWriter
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setCodec(new SimpleTextCodec());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE); 

        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            // indicizza i documenti
            indexTextFiles(writer, Paths.get(DATA_DIR));
        }

        System.out.println("Indicizzazione completata con successo.");
    }

    private static void search(Directory indexDirectory) throws IOException, ParseException {
        try (IndexReader reader = DirectoryReader.open(indexDirectory); Scanner scanner = new Scanner(System.in)) {
            IndexSearcher searcher = new IndexSearcher(reader); // IndexSearcher lets you search on an IndexReader and lets you run Query objects
            PerFieldAnalyzerWrapper analyzer = getAnalyzer(); //riprendi lo stesso analyzer usato per indicizzare

            while (true) {
                System.out.println("\nInserisci la query (es. 'nome \"file.txt\"' o 'contenuto query') o 'q' per uscire:");
                String line = scanner.nextLine();

                if ("q".equalsIgnoreCase(line)) {
                    break;
                }
                // verifica che l'input sia nome-query E contenuto-query
                String[] parts = line.split(" ", 2);
                if (parts.length < 2) {
                    System.out.println("Sintassi query non valida. Usare 'nome <termine>' o 'contenuto <termine>'.");
                    continue;
                }

                String field;
                if ("nome".equalsIgnoreCase(parts[0])) {
                    field = "filename";
                } else if ("contenuto".equalsIgnoreCase(parts[0])) {
                    field = "content";
                } else {
                    System.out.println("Campo non valido. Usare 'nome' o 'contenuto'.");
                    continue;
                }
                String queryString = parts[1];

                QueryParser parser = new QueryParser(field, analyzer);
                Query query = parser.parse(queryString);

                System.out.println("Ricerca per '" + query.toString(field) + "' nel campo '" + field + "'");

                TopDocs results = searcher.search(query, 10);
                ScoreDoc[] hits = results.scoreDocs;

                System.out.println("Trovati " + hits.length + " risultati.");
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d = searcher.doc(docId); // Utilizza il metodo non deprecato
                    System.out.println((i + 1) + ". " + d.get("filename") + " (Score: " + hits[i].score + ")");
                }
            }
        }
        finally {
            System.out.println("Programma terminato.");
        }
    }

    private static void indexTextFiles(IndexWriter writer, Path path) throws IOException {
        File file = path.toFile();
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    indexTextFiles(writer, f.toPath());
                }
            }
        } else if (file.getName().toLowerCase().endsWith(".txt")) {
            System.out.println("Indicizzando il file: " + file.getAbsolutePath());
            Document doc = new Document();
            doc.add(new TextField("filename", file.getName(), Field.Store.YES));
            doc.add(new TextField("content", new String(Files.readAllBytes(path)), Field.Store.YES));
            writer.addDocument(doc);
        }
    }
}
