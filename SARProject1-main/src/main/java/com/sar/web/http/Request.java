package com.sar.web.http;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author pedroamaral 
 * 
 * Class that stores all information about a HTTP request
 * Incomplete Version 24/25
 */
public class Request {
    private static final Logger logger = LoggerFactory.getLogger(Request.class);

    private final String clientAddress;
    private final int clientPort;
    private final int serverPort;


    public Headers headers; // stores the HTTP headers of the request
    public Properties cookies; //stores cookies received in the Cookie Headers
    private Properties postParameters; //stores POST parameters if request is a POST
    public String text;     //store possible contents in an HTTP request (for example POST contents)
    public String version;
    public String method;
    public String urlText;
    /** 
     * Creates a new instance of HTTPQuery
     * @param _UserInterface   log object
     * @param id    log id
     * @param LocalPort local HTTP server port
     */
    public Request(String clientAddress, int clientPort, int serverPort) {
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
        this.serverPort = serverPort;
        this.headers = new Headers();
        this.cookies = new Properties();
        this.postParameters = new Properties();
    }

    /*DONE: TASK2 */

    public void parseRequest(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));

        // 1. Primeira linha: método, URL, versão
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            throw new Exception("Empty request line");
        }

        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new Exception("Invalid request line: " + requestLine);
        }

        method = parts[0].trim();
        urlText = parts[1].trim();
        version = parts[2].trim();

        // 2. Ler todos os headers com o método novo
        headers.readHeaders(reader);

        // 3. Parse de cookies (se existirem)
        parseCookies();

        // 4. Se for POST, ler o corpo
        if ("POST".equalsIgnoreCase(method)) {
            String contentLengthStr = headers.getHeaderValue("Content-Length");
            int contentLength = contentLengthStr != null ? Integer.parseInt(contentLengthStr) : 0;

            if (contentLength > 0) {
                char[] body = new char[contentLength];
                int read = reader.read(body, 0, contentLength);
                text = new String(body, 0, read);

                // Verifica se é formulário
                String contentType = headers.getHeaderValue("Content-Type");
                if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                    for (String param : text.split("&")) {
                        String[] kv = param.split("=");
                        if (kv.length == 2) {
                            String key = URLDecoder.decode(kv[0], "UTF-8");
                            String value = URLDecoder.decode(kv[1], "UTF-8");
                            postParameters.setProperty(key, value);
                        }
                    }
                }
            }
        }
    }


    //Method to getClienAddress
    public String getClientAddress() {
        return clientAddress;
    }

    //Method to getClientPort
    public int getClientPort() {
        return clientPort;
    }

    // Cookie handling get cokkie header value and parse it in to the cookies properties
    public void parseCookies() {
        String cookieHeader = headers.getHeaderValue("Cookie");
        if (cookieHeader != null) {
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2) {
                    cookies.setProperty(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

     /**
     * Get a header property value
     * @param hdrName   header name
     * @return          header value
     */
    public String getHeaderValue(String hdrName) {
        return headers.getHeaderValue(hdrName);
    }
    
    /**
     * Set a header property value
     * @param hdrName   header name
     * @param hdrVal    header value
     */
    public void setHeader(String hdrName, String hdrVal) {
        headers.setHeader(hdrName, hdrVal);
    }

    
    /** Returns the Cookie Properties object */
    public Properties getCookies () {
        return this.cookies;
    }
    
    public Properties getPostParameters() {
        return postParameters;
    }
    
    /**
     * Remove a header property name
     * @param hdrName   header name
     * @return true if successful
     */
    public boolean removeHeader(String hdrName) {
        return headers.removeHeader(hdrName);
    } 
}