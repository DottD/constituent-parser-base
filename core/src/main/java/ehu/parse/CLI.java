package ehu.parse;


import ixa.kaflib.KAFDocument;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import ehu.heads.CollinsHeadFinder;
import ehu.heads.HeadFinder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.slf4j.LoggerFactory;

/**
 * Constituent Parsing for 4 languages: English, Spanish, French and Italian
 * 
 * @version 1.0
 * 
 */

public class CLI {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CLI.class);
  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final Integer DEFAULT_PORT = 55555;
  // TODO allow to specify a port

  /**
   * 
   * 
   * BufferedReader (from standard input) and BufferedWriter are opened. The
   * module takes KAF and reads the header, and the text elements and uses
   * Annotate class to provide constituent parsing of sentences, which are
   * provided via standard output.
   * 
   * @param args
   * @throws Exception 
   */

  public static void main(String[] args) throws Exception {

    System.out.printf(args.toString());

    Namespace parsedArguments = null;

    // create Argument Parser
    ArgumentParser parser = ArgumentParsers.newArgumentParser(
        "ehu-parse-1.0.jar").description(
        "ehu-parse-1.0 is a multilingual Constituent Parsing module "
            + "developed by IXA NLP Group based on Apache OpenNLP API.\n");

    // specify language
    parser
        .addArgument("-l", "--lang")
        .choices("en", "es", "it", "fr")
        .required(true)
        .help("It is REQUIRED to choose a language to perform annotation with constituent-parse-base");

    parser
        .addArgument("--noHeads")
        .action(Arguments.storeFalse())
        .required(false)
        .help("Do not print headWords");

    parser
        .addArgument("--server")
        .action(Arguments.storeTrue())
        .help("Enables server mode.");

    parser
        .addArgument("--port")
        .help(String.format("Listening port for server mode (default %d)", DEFAULT_PORT));
    
    parser
        .addArgument("-t", "--timestamp")
        .action(Arguments.storeTrue())
        .help("flag to make timestamp static for continous integration testing");

    /*
     * Parse the command line arguments
     */

    // catch errors and print help
    try {
      parsedArguments = parser.parseArgs(args);
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.out
          .println("Run java -jar target/ehu-parse-1.0.jar -help for details");
      System.exit(1);
    }

    /*
     * Check if server mode is requested 
     */

    if (parsedArguments.getBoolean("server")) {
      /*
      * Server mode
      */
      int timeoutInSeconds = -1;

      try {
        Integer port;
        if (parsedArguments.get("port") == null){
          port = DEFAULT_PORT;
        } else {
          port = Integer.parseInt(parsedArguments.getString("port"));
        }
        System.out
          .println(String.format("Port = %d", port));

        final HttpServer httpServer = new HttpServer();
        NetworkListener nl = new NetworkListener("ehu-parse-server", DEFAULT_HOST, port);
        httpServer.addListener(nl);

        String lang;
        if (parsedArguments.get("lang") == null) { 
          lang = "it";
          System.out.println("Set default language: IT");
        } else {
          lang = parsedArguments.getString("lang");
        }
        Boolean noHeads = parsedArguments.getBoolean("noHeads");
        RequestsHandler handler = new RequestsHandler(lang, noHeads);
        handler.setRequestURIEncoding(Charset.forName("UTF-8"));

        httpServer.getServerConfiguration().setSessionTimeoutSeconds(timeoutInSeconds);
        httpServer.getServerConfiguration().setMaxPostSize(4194304);
        httpServer.getServerConfiguration().addHttpHandler(handler, "/ehu");

        httpServer.start();
        Thread.currentThread().join();
      } catch (Exception e) {
          LOGGER.error("error running " + DEFAULT_HOST + ":" + DEFAULT_PORT);
          e.printStackTrace();
      }


    } else {
      /*
      * Load language and headFinder parameters
      */
    
      BufferedReader breader = null;
      BufferedWriter bwriter = null;
      
      try {
        breader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        bwriter = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));
        KAFDocument kaf = KAFDocument.createFromStream(breader);
        // language parameter
        String lang;
        if (parsedArguments.get("lang") == null) { 
            lang = kaf.getLang();
          }
          else { 
          lang =  parsedArguments.getString("lang");
          }
        // static timestamp for continuous integration
        if (parsedArguments.getBoolean("timestamp") == true) {
          kaf.addLinguisticProcessor("constituency","ehu-parse-"+lang,"now", "1.0");
        }
        else {
          kaf.addLinguisticProcessor("constituency", "ehu-parse-"+lang, "1.0");
        }
        Annotate annotator = new Annotate(lang);
        
        // choosing HeadFinder: (Collins rules for English and derivations of it
        // for other languages; sem (Semantic headFinder re-implemented from
        // Stanford CoreNLP).
        // Default: sem (semantic head finder).
        

        if (parsedArguments.getBoolean("noHeads")) {
          HeadFinder headFinder = new CollinsHeadFinder(lang);

          // parse with heads
          bwriter.write(annotator.parseWithHeads(kaf, headFinder));
        }
          // parse without heads
        else {
          bwriter.write(annotator.parse(kaf));
        }

        bwriter.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

  }
}
