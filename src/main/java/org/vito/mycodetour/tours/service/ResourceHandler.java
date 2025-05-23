package org.vito.mycodetour.tours.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 本地资源拦截处理
 *
 * @author vito
 * @since 11.0
 * Created on 2025/5/1
 */
public class ResourceHandler implements CefResourceHandler {
    private static final Logger LOG = Logger.getInstance(ResourceHandler.class);

    private InputStream inputStream;
    private String mimeType;
    private Project project;
    private Map<String, String> values = Collections.emptyMap();

    public ResourceHandler(Project project) {
        this.project = project;
    }

    public ResourceHandler(Project project, Map<String, String> values) {
        this.project = project;
        this.values = values;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        String url = request.getURL();
        // 如果是 jbcefbrowser 路径，直接返回 false，让系统处理
        if (url.startsWith("file:///jbcefbrowser/")) {
            return false;
        }

        // 解析URL路径（例如 "myapp:///html/index.html"）
        if (url.startsWith("file:///mycodetour/") && url.endsWith("index.html")) {
            String resourcePath = url.replace("file:///mycodetour", "");
            String markdownHtml = TinyTemplateEngine.render(resourcePath, values);
            inputStream = new ByteArrayInputStream(markdownHtml.getBytes(StandardCharsets.UTF_8));
        } else if (url.startsWith("file:///mycodetour/")) {
            String resourcePath = url.replace("file:///mycodetour", "");
            inputStream = getClass().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                return false;
            }
        } else {
            String resourcePath = url.replace("file:///", "");
            VirtualFile resourceFile = VirtualFileManager.getInstance().findFileByNioPath(new File(project.getBasePath() + "/" + resourcePath).toPath());
            if (resourceFile == null) {
                return false;
            }
            try {
                inputStream = resourceFile.getInputStream();
            } catch (IOException e) {
                LOG.error(e);
                return false;
            }
        }

        // 根据文件后缀设置MIME类型
        if (url.endsWith(".html")) {
            mimeType = "text/html";
        } else if (url.endsWith(".css")) {
            mimeType = "text/css";
        } else if (url.endsWith(".js")) {
            mimeType = "application/javascript";
        } else if (url.endsWith(".png")) {
            mimeType = "image/png";
        } else if (url.endsWith(".jpg") || url.endsWith(".jpeg")) {
            mimeType = "image/jpeg";
        } else if (url.endsWith(".gif")) {
            mimeType = "image/gif";
        } else if (url.endsWith(".svg")) {
            mimeType = "image/svg+xml";
        } else if (url.endsWith(".excalidraw")) {
            mimeType = "application/json";
        } else {
            mimeType = "application/octet-stream";
        }

        callback.Continue();
        return true;
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setMimeType(mimeType);
        response.setStatus(200);
        responseLength.set(-1); // -1表示流式传输
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        try {
            int available = inputStream.available();
            if (available == 0) {
                bytesRead.set(0);
                return false;
            }
            bytesRead.set(inputStream.read(dataOut, 0, Math.min(bytesToRead, available)));
            return bytesRead.get() > 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void cancel() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}