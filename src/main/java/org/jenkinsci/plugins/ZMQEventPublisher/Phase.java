/*
 * Copyright 2013 Hewlett-Packard Development Company, L.P.
 * Copyright Authors of the Jenkins Notification Plugin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.ZMQEventPublisher;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.Executor;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jenkinsci.plugins.ZMQEventPublisher.model.BuildState;
import org.jenkinsci.plugins.ZMQEventPublisher.model.JobState;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public enum Phase {
    STARTED, COMPLETED, FINISHED;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public String handlePhase(Run run, String status, String masterName, TaskListener listener) {
        Jenkins jenkins = Jenkins.getInstance();
        HudsonNotificationProperty property = (HudsonNotificationProperty)
            run.getParent().getProperty(HudsonNotificationProperty.class);
        HudsonNotificationProperty.HudsonNotificationPropertyDescriptor globalProperty =
            (HudsonNotificationProperty.HudsonNotificationPropertyDescriptor)
                jenkins.getDescriptor(HudsonNotificationProperty.class);
        if ((property != null && property.isEnabled()) ||
                (globalProperty != null && globalProperty.isGloballyEnabled())) {
            return buildMessage(run.getParent(), run, status, masterName);
        }
        return null;
    }

    private Gson gson = new GsonBuilder().setFieldNamingPolicy(
        FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private String buildMessage(Job job, Run run, String status, String masterName) {
        JobState jobState = new JobState();
        jobState.setName(job.getName());
        jobState.setUrl(job.getUrl());
        BuildState buildState = new BuildState();
        buildState.setNumber(run.number);
        buildState.setUrl(run.getUrl());
        buildState.setPhase(this);
        buildState.setStatus(status);

        Jenkins jenkins = Jenkins.getInstance();
        String rootUrl = jenkins.getRootUrl();
        if (rootUrl != null) {
            buildState.setFullUrl(rootUrl + run.getUrl());
        }
        if (masterName != null) {
            buildState.setHostName(masterName);
        }

        Executor executor = run.getExecutor();
        if (executor != null) {
            Computer computer = executor.getOwner();
            if (computer != null) {
                buildState.setNodeName(computer.getName());
            }
        }

        jobState.setBuild(buildState);

        EnvVars envVars = getEnvVars(run);
        ParametersAction paramsAction = run.getAction(ParametersAction.class);
        if (paramsAction != null && run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) run;
            EnvVars env = new EnvVars();
            for (ParameterValue value : paramsAction.getParameters()) {
                if (!value.isSensitive()) {
                    if (envVars.containsKey(value.getName())) {
                        String envvar = envVars.get(value.getName());
                        env.put(value.getName(), envvar);
                    } else {
                        value.buildEnvVars(build, env);
                    }
                }
            }
            buildState.setParameters(env);
        }



        return gson.toJson(jobState);
    }

    private EnvVars getEnvVars(Run build) {
        try {
            return build.getEnvironment(new LogTaskListener(Logger.getLogger(getClass().getCanonicalName()), Level.INFO));
        } catch (IOException e) {
        } catch (InterruptedException e) {
        }
        return null;
    }
}
