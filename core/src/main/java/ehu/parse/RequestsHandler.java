package ehu.parse;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import ixa.kaflib.KAFDocument;
import ehu.heads.CollinsHeadFinder;
import ehu.heads.HeadFinder;

public class RequestsHandler extends HttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestsHandler.class);

    public static Map<OutputFormat, String> contentTypes = new HashMap<>();

    public static enum OutputFormat {
        READABLE, JSON, XML, CONLL, NAF, TEXTPRO
    }

    static {
        contentTypes.put(OutputFormat.CONLL, "text/plain");
        contentTypes.put(OutputFormat.XML, "text/xml");
        contentTypes.put(OutputFormat.NAF, "text/xml");
        contentTypes.put(OutputFormat.JSON, "text/json");
        contentTypes.put(OutputFormat.TEXTPRO, "text/plain");
        contentTypes.put(OutputFormat.READABLE, "text/plain");
    }

    private String lang;
    private Boolean noHeads;

    public RequestsHandler(String lang, Boolean noHeads) {
        this.lang = lang;
        this.noHeads = noHeads;
    }

    public void writeOutput(Response response, String contentType, String output) throws IOException {
        response.setContentType(contentType);
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.getWriter().write(output);
    }

    @Override
    public void service(Request request, Response response) throws Exception {

        request.setCharacterEncoding("UTF-8");

        Buffer postBody = request.getPostBody(1024);
        String text = postBody.toStringContent();

        if (request.getParameter("text") != null) {
            text = request.getParameter("text");
        }

        InputStream inputStream = new ByteArrayInputStream(text.getBytes());

        // Read the stream
        BufferedReader breader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        KAFDocument kaf = KAFDocument.createFromStream(breader);
        if (this.lang == null) {
            this.lang = kaf.getLang();
        }
        kaf.addLinguisticProcessor("constituency", "ehu-parse-" + this.lang, "now", "1.0");
        // Process
        Annotate annotator = new Annotate(lang);
        String parsedText;
        if (this.noHeads) {
            HeadFinder headFinder = new CollinsHeadFinder(lang);

            // parse with heads
            parsedText = annotator.parseWithHeads(kaf, headFinder);
        }
        // parse without heads
        else {
            parsedText = annotator.parse(kaf);
        }

        LOGGER.debug("Text: {}", text);

        // Write to output stream
        writeOutput(response, contentTypes.get(OutputFormat.XML), parsedText);
    }
}