package de.oai.build;

import com.android.build.gradle.AppPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class AndroidApplicationShim implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(AppPlugin.class);
    }
}
