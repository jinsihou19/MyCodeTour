package org.vito.mycodetour.tours.domain;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 开箱指南
 *
 * @author vito
 * Created on 2025/01/01
 */
public class OnboardingAssistant {

    private static final String DEMO_FILENAME = "tours/projectIntroduction-demo.tour";
    public static final String DEMO_ID = "70abd0f5-3fb7-4309-a9bf-97afeb28aa9b";
    private static final Logger LOG = Logger.getInstance(OnboardingAssistant.class);

    private static OnboardingAssistant instance;
    private Tour tour = null;

    public OnboardingAssistant() {

        try (InputStream is = this.getClass().getClassLoader()
                .getResourceAsStream(DEMO_FILENAME)) {
            if (is == null) return;
            tour = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), Tour.class);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    public static OnboardingAssistant getInstance() {
        if (instance == null || instance.getTour() == null)
            instance = new OnboardingAssistant();
        return instance;
    }

    public Tour getTour() {
        return tour;
    }
}