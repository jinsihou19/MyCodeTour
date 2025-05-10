package org.vito.mycodetour.tours.service;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import org.vito.mycodetour.tours.domain.Step;
import org.vito.mycodetour.tours.state.StateManager;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.regex.Pattern;

import static org.vito.mycodetour.tours.service.Utils.renderFullDoc;

/**
 * Renders a Popup which includes the Step Documentation
 *
* @author vito
* Created on 2025/1/1
 */
public class StepRendererPane extends JPanel {

    private static final Logger LOG = Logger.getInstance(StepRendererPane.class);

    private static final Pattern JAVA_FILE_LINE_PATTERN = java.util.regex.Pattern.compile("([\\w.]+\\.java):(\\d+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("^([a-z][a-z0-9_$]*\\\\.)*[A-Z][a-zA-Z0-9_$]*$");
    private static final Pattern CLASS_REGEX_PATTERN = Pattern.compile("^([a-z][a-z0-9_$]*\\.)*[A-Z][a-zA-Z0-9_$]*#([a-zA-Z0-9_$]+)$");
    private static final Pattern JBCEF_METHOD_PATTERN = Pattern.compile("^file:///jbcefbrowser/([a-z][a-z0-9_$]*\\.)*[A-Z][a-zA-Z0-9_$]*$");
    private static final Pattern JBCEF_CLASS_REGEX_PATTERN = Pattern.compile("^file:///jbcefbrowser/([a-z][a-z0-9_$]*\\.)*[A-Z][a-zA-Z0-9_$]*#([a-zA-Z0-9_$]+)$");

    private final Step step;
    private final Project project;

    public StepRendererPane(Step step, Project project) {
        super(true);
        this.step = step;
        this.project = project;
        init();
    }

    private boolean matchCode(String url) {
        return JAVA_FILE_LINE_PATTERN.matcher(url).matches()
                || JBCEF_METHOD_PATTERN.matcher(url).matches()
                || JBCEF_CLASS_REGEX_PATTERN.matcher(url).matches()
                || METHOD_PATTERN.matcher(url).matches()
                || CLASS_REGEX_PATTERN.matcher(url).matches();
    }

    private JComponent markdownJCEFHtmlPanelForRender() {
        final String stepDoc = renderFullDoc(
                StateManager.getInstance().getState(project).getStepMetaLabel(step.getTitle()),
                step.getDescription(),
                step.getFile() != null ? String.format("%s:%s", step.getFile(), step.getLine()) : "");

//        CefApp.getInstance().registerSchemeHandlerFactory(
//                "file",
//                "",
//                (cefBrowser, cefFrame, s, cefRequest) -> new ResourceHandler());

        JBCefBrowser browser = new JBCefBrowser();

        browser.loadHTML(stepDoc);
        browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request,
                                          boolean user_gesture, boolean is_redirect) {
                // 处理跳转
                return dealWithJCEFLink(request.getURL());
            }

            @Override
            public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
                LOG.warn("Canceling navigation for url:" + target_url + " (user_gesture=" + user_gesture + ")");
                // 禁止其他页面的跳转
                return true;
            }

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator, BoolRef disableDefaultHandling) {
                return new CefResourceRequestHandlerAdapter() {
                    @Override
                    public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
                        String url = request.getURL();
                        if (url.startsWith("file:///") && !isIndex(url)) {
                            return new ResourceHandler(project);
                        }
                        // 放行非必要处理请求
                        return null;
                    }
                };
            }
        }, browser.getCefBrowser());

        return browser.getComponent();
    }

    private boolean isIndex(String url) {
        return url.startsWith("file:///jbcefbrowser/") && url.endsWith("url=about:blank");
    }

    private boolean dealWithJCEFLink(String link) {
        if (link.startsWith("http://") || link.startsWith("https://")) {
            BrowserUtil.browse(link);
            return true;
        } else if (link.startsWith(Navigator.NAVIGATE) || matchCode(link)) {
            Navigator.navigateCode(link, project);
            return true;
        } else if (link.startsWith(Navigator.TOUR)) {
            Navigator.navigateTour(link, project);
            return true;
        }
        return false;
    }

    protected void init() {
        setLayout(new BorderLayout());
        add(markdownJCEFHtmlPanelForRender(), BorderLayout.CENTER);
    }
}
