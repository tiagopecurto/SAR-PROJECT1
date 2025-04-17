package com.sar.web.handler;

import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;



public class StaticFileHandler extends AbstractRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(StaticFileHandler.class);
    private final String baseDirectory;
    private final String homeFileName;
    private final Map<String, String> mimeTypes;

    public StaticFileHandler(String baseDirectory, String homeFileName) {
        this.baseDirectory = baseDirectory;
        this.homeFileName = homeFileName;
        this.mimeTypes = MIME_TYPES;
    }

    private static final Map<String, String> MIME_TYPES = new HashMap<>();


    //private static final Map<String, String> sessions = new HashMap<>();
    private static final Map<String, Long> sessions = new HashMap<>();

    private static final long SESSION_DURATION_MS = 10 * 60 * 1000; // 2 minutos
    
    static {
        MIME_TYPES.put(".html", "text/html");
        MIME_TYPES.put(".htm", "text/html");
        MIME_TYPES.put(".css", "text/css");
        MIME_TYPES.put(".js", "text/javascript");
        MIME_TYPES.put(".jpg", "image/jpeg");
        MIME_TYPES.put(".jpeg", "image/jpeg");
        MIME_TYPES.put(".png", "image/png");
        MIME_TYPES.put(".gif", "image/gif");
    }

    @Override
    protected void handleGet(Request request, Response response) {
        String path = request.urlText;

        if (path.equals("/logout")) {
            String cookieHeader = request.getHeaderValue("Cookie");
            if (cookieHeader != null && cookieHeader.contains("session=")) {
                String[] cookies = cookieHeader.split(";");
                for (String cookie : cookies) {
                    cookie = cookie.trim();
                    if (cookie.startsWith("session=")) {
                        String token = cookie.substring("session=".length());
                        sessions.remove(token);
                        break;
                    }
                }
            }
    
            response.setCode(ReplyCode.OK);
            response.setVersion(request.version);
            response.setHeader("Set-Cookie", "session=; Path=/; Max-Age=0; HttpOnly");
            response.setTextHeaders("<html><body><h1>Logout efetuado</h1><p>Sessão terminada com sucesso.</p></body></html>");
            return;
        }

        if (path.equals("/")) {
            path = "/" + homeFileName;
        }
    
        String fullPath = baseDirectory + path;
        File file = new File(fullPath);

        // Flag de sessão válida
        boolean authenticated = false;

        // Verificação de sessão via cookie
        String cookieHeader = request.getHeaderValue("Cookie");
        if (cookieHeader != null && cookieHeader.contains("session=")) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                cookie = cookie.trim();
                if (cookie.startsWith("session=")) {
                    String token = cookie.substring("session=".length());
                    /*if (sessions.containsKey(token)) {
                        logger.info("Sessão válida via cookie: " + token);
                        // Sessão válida → pode continuar sem pedir login
                        authenticated = true;
                        break;
                    } */
                    Long createdAt = sessions.get(token);
                    if (createdAt != null) {
                        long now = System.currentTimeMillis();
                        if (now - createdAt < SESSION_DURATION_MS) {
                            logger.info("Sessão válida via cookie: " + token);
                            authenticated = true;
                            break;
                        } else {
                            logger.info("Sessão expirada: " + token);
                            sessions.remove(token); // apagar sessão expirada
                            response.setHeader("Set-Cookie", "session=; Path=/; Max-Age=0; HttpOnly"); // limpa cookie
                            response.setCode(ReplyCode.UNAUTHORIZED);
                            response.setVersion(request.version);
                            response.setHeader("WWW-Authenticate", "Basic realm=\"SAR\"");
                            response.setTextHeaders("<html><body><h1>401 Unauthorized</h1><p>Sessão expirada. Por favor autentique-se novamente.</p></body></html>");
                            return;
                        }
                    }
                    else {
                        // Sessão inválida → forçar login
                        response.setHeader("Set-Cookie", "session=; Path=/; Max-Age=0; HttpOnly"); // limpa cookie
                        response.setCode(ReplyCode.UNAUTHORIZED);
                        response.setVersion(request.version);
                        response.setHeader("WWW-Authenticate", "Basic realm=\"SAR\"");
                        response.setTextHeaders("<html><body><h1>401 Unauthorized</h1><p>Autenticação necessária (sessão inválida)</p></body></html>");
                        return;
                    }
                }
            }
        }
        if(!authenticated){
            String authHeader = request.getHeaderValue("Authorization");
            
            if (authHeader == null || !authHeader.equals("Basic " + Base64.getEncoder().encodeToString("Username:Pass".getBytes()))) {
                response.setCode(ReplyCode.UNAUTHORIZED);
                response.setVersion(request.version);
                response.setHeader("WWW-Authenticate", "Basic realm=\"SAR\"");
                response.setTextHeaders("<html><body><h1>401 Unauthorized</h1><p>Autenticação necessária</p></body></html>");
                return;
            }

            String sessionToken = java.util.UUID.randomUUID().toString();
            sessions.put(sessionToken, System.currentTimeMillis()); // guarda timestamp atual
            response.setHeader("Set-Cookie", "session=" + sessionToken + "; Path=/; HttpOnly");
            

            authenticated = true;
        }
        /* 
        String sessionToken = "SESSION123"; 
        request.headers.setHeader("X-Session-Token", sessionToken);
        response.setHeader("Set-Cookie", "session=" + sessionToken + "; Path=/; HttpOnly");
        */

        try {
            if (file.exists() && file.isFile()) {
    
                String ifModSince = request.getHeaderValue("If-Modified-Since");
                if (ifModSince != null) {
                    try {
                        DateFormat httpFormat = new SimpleDateFormat("EE, d MMM yyyy HH:mm:ss zz", Locale.UK);
                        httpFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                        Date clientDate = httpFormat.parse(ifModSince);
                        Date fileDate = new Date(file.lastModified());
    
                        if (!fileDate.after(clientDate)) {
                            response.setCode(ReplyCode.NOTMODIFIED);
                            response.setVersion(request.version);
                            response.setDate();
                            logger.info("Not modified: {}. Returning 304.", fullPath);
                            return;
                        }
                    } catch (Exception e) {
                        logger.warn("Erro ao interpretar If-Modified-Since: {}", ifModSince);
                    }
                }
    
                response.setCode(ReplyCode.OK);
                response.setVersion(request.version);
                response.setFileHeaders(file, getMimeType(path));
                logger.info("Serving file: {}", fullPath);
    
            } 
            else {
                logger.warn("File not found: {}. Returning 404 error.", fullPath);
                response.setCode(ReplyCode.NOTFOUND);
                response.setVersion(request.version);
            }
        } catch (Exception e) {
            logger.error("Error handling GET request for file: {}", fullPath, e);
            response.setError(ReplyCode.BADREQ, request.version);
        }
    }
    

    @Override
    protected void handlePost(Request request, Response response) {
        // Static files don't handle POST requests
        logger.error("StaticFileHandler does not handle POST requests.");
        response.setError(ReplyCode.NOTIMPLEMENTED, request.version);
    }

    private String getMimeType(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = path.substring(dotIndex).toLowerCase();
            return mimeTypes.getOrDefault(extension, DEFAULT_MIME_TYPE);
        }
        return DEFAULT_MIME_TYPE;
    }
}