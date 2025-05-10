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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

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

    public ResourceHandler(Project project) {
        this.project = project;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        String url = request.getURL();
        // 如果是 jbcefbrowser 路径，直接返回 false，让系统处理
        if (url.startsWith("file:///jbcefbrowser/")) {
            return false;
        }

        // 解析URL路径（例如 "myapp:///html/index.html"）
        String resourcePath = url.replace("file:///", "");

        // 从类路径加载资源
//        inputStream = getClass().getResourceAsStream(resourcePath);
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

        // 根据文件后缀设置MIME类型
        if (resourcePath.endsWith(".html")) {
            mimeType = "text/html";
        } else if (resourcePath.endsWith(".css")) {
            mimeType = "text/css";
        } else if (resourcePath.endsWith(".js")) {
            mimeType = "application/javascript";
        } else if (resourcePath.endsWith(".png")) {
            mimeType = "image/png";
        } else if (resourcePath.endsWith(".jpg") || resourcePath.endsWith(".jpeg")) {
            mimeType = "image/jpeg";
        } else if (resourcePath.endsWith(".gif")) {
            mimeType = "image/gif";
        } else if (resourcePath.endsWith(".svg")) {
            mimeType = "image/svg+xml";
        } else if (resourcePath.endsWith(".excalidraw")) {
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