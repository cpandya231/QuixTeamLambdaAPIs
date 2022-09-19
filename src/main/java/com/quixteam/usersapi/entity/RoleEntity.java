package com.quixteam.usersapi.entity;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

import java.util.List;
import java.util.Set;

@DynamoDBTable(tableName = "Roles")
public class RoleEntity {
    @DynamoDBHashKey
    private String name;
    @DynamoDBAttribute
    private List<Module> modules;
    @DynamoDBAttribute
    private List<String> childRoles;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Module> getModules() {
        return modules;
    }

    public void setModules(List<Module> modules) {
        this.modules = modules;
    }

    public List<String> getChildRoles() {
        return childRoles;
    }

    public void setChildRoles(List<String> childRoles) {
        this.childRoles = childRoles;
    }
}