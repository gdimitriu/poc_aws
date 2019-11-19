package poc_aws.utils.auth;
/*
 Copyright (c) 2019 Gabriel Dimitriu All rights reserved.
 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

 This file is part of poc_aws project.

 poc_aws is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 poc_aws is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with poc_aws.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Policy {
    @SerializedName("Version")
    private String version = "2008-10-17";
    @SerializedName("Statement")
    private List<PolicyStatement> statements;

    public Policy() {
        statements = new ArrayList<>();
        statements.add(new PolicyStatement());
    }

    public Policy(String version) {
        this();
        this.version = version;
    }

    public Policy withSid(String sid) {
        statements.get(0).setSid(sid);
        return this;
    }

    public Policy withEffect(String effect) {
        statements.get(0).setEffect(effect);
        return this;
    }

    public Policy withAction(String action) {
        statements.get(0).addAction(action);
        return this;
    }

    public Policy withResource(String resource) {
        statements.get(0).addResource(resource);
        return this;
    }

    public Policy withPrincipal(String service, String rule) {
        statements.get(0).addPrincipal(service, rule);
        return this;
    }

    public Policy withCondition(String condition, String type, String resource) {
        statements.get(0).addCondition(condition, type, resource);
        return this;
    }

    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}

class PolicyStatement {
    @SerializedName("Sid")
    private String sid;

    @SerializedName("Effect")
    private String effect;

    @SerializedName("Principal")
    private Map<String, List<String>> principal = null;

    @SerializedName("Action")
    private List<String> actions = null;

    @SerializedName("Resource")
    private List<String> resource = null;

    @SerializedName("Condition")
    private Map<String, Map<String, List<String>>> conditions = null;

    public void setSid(String sid) {
        this.sid = sid;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public void addAction(String action) {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        this.actions.add(action);
    }

    public void addResource(String resource) {
        if (this.resource == null) {
            this.resource = new ArrayList<>();
        }
        this.resource.add(resource);
    }

    public void addPrincipal(String service, String rule) {
        if (principal == null) {
            principal = new HashMap<>();
        }
        if (principal.containsKey(service)) {
            principal.get(service).add(rule);
        } else {
            List<String> rules = new ArrayList<>();
            rules.add(rule);
            principal.put(service, rules);
        }
    }
    public void addCondition(String condition, String type, String resource) {
        if (conditions == null) {
            conditions = new HashMap<>();
        }
        if (conditions.containsKey(condition)) {
            Map<String, List<String>> theCondition = conditions.get(condition);
            if (theCondition != null && theCondition.containsKey(type)) {
                theCondition.get(type).add(resource);
            } else if (theCondition == null) {
                theCondition = new HashMap<>();
                List<String> resources = new ArrayList<>();
                resources.add(resource);
                theCondition.put(type, resources);
            } else {
                List<String> resources = new ArrayList<>();
                resources.add(resource);
                theCondition.put(type, resources);
            }
        } else {
            Map<String, List<String>> theCondition = new HashMap<>();
            List<String> resources = new ArrayList<>();
            resources.add(resource);
            theCondition.put(type, resources);
            conditions.put(condition, theCondition);
        }
    }
}
