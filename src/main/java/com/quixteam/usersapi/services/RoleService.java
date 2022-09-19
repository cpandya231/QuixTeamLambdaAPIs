package com.quixteam.usersapi.services;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quixteam.usersapi.entity.Module;
import com.quixteam.usersapi.entity.RoleEntity;
import com.quixteam.usersapi.util.LambdaUtil;

import java.util.*;
import java.util.stream.Collectors;

public class RoleService {
    private final ObjectMapper objectMapper;
    private final DynamoDBMapper dynamoDBMapper;


    public RoleService(ObjectMapper objectMapper, DynamoDBMapper dynamoDBMapper) {
        this.objectMapper = objectMapper;
        this.dynamoDBMapper = dynamoDBMapper;
    }

    private List<String> getRoleNames() {
        var roleEntities = getRoles();
        return roleEntities.stream().map(RoleEntity::getName).collect(Collectors.toList());
    }

    private List<RoleEntity> getRoles() {

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        return dynamoDBMapper.scan(RoleEntity.class, scanExpression);
    }


    public APIGatewayV2HTTPResponse createRole(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        var body = event.getBody();
        String output = "{ \"message\": \"Role Created successfully\" }";
        int statusCode;

        var roleRequest = objectMapper.readValue(body, RoleEntity.class);
        List<String> roleNames = getRoleNames();

        if (roleNames.stream().anyMatch(role -> role.equalsIgnoreCase(roleRequest.getName()))) {
            output = String.format("{ \"message\": \"%s\" }", "Role already exist with " + roleRequest.getName());
            statusCode = 409;
        } else {
            saveRole(roleRequest);
            statusCode = 200;
        }
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(statusCode);
        return response;
    }

    private void saveRole(RoleEntity roleEntity) {
        dynamoDBMapper.save(roleEntity);
    }

    public APIGatewayV2HTTPResponse updateRole(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        var body = event.getBody();
        var pathParams = event.getPathParameters();
        var roleName = pathParams.get("roleName");
        String output = "{ \"message\": \"Role Updated successfully\" }";
        int statusCode;

        var childRolesRequest = objectMapper.readValue(body, List.class);
        List<String> roleNames = getRoleNames();

        if (childRolesRequest.stream().anyMatch(role -> !roleNames.contains(role))) {
            output = String.format("{ \"message\": \"%s\" }", "Invalid roles provided " + childRolesRequest);
            statusCode = 400;
        } else {
            Optional<RoleEntity> roleEntityOptional = getRoleByName(roleName);

            if (roleEntityOptional.isEmpty()) {
                output = String.format("{ \"message\": \"%s\" }", "Role does not exist for" + roleName);
                statusCode = 404;
            } else {
                var roleEntity = roleEntityOptional.get();
                var childRoles = roleEntity.getChildRoles();
                if (LambdaUtil.isEmptyCollection(childRoles)) {
                    childRoles = new HashSet<>();
                }
                childRoles.addAll(childRolesRequest);
                roleEntity.setChildRoles(childRoles);
                saveRole(roleEntity);
                statusCode = 200;
            }
        }

        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(statusCode);
        return response;
    }


    public APIGatewayV2HTTPResponse getAllRoles() throws JsonProcessingException {

        var roleEntities = getRoles();
        var output = objectMapper.writeValueAsString(roleEntities);
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(200);
        return response;
    }


    public APIGatewayV2HTTPResponse createModule(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        var pathParams = event.getPathParameters();
        var roleName = pathParams.get("roleName");
        var body = event.getBody();
        String output = "{ \"message\": \"Module Added successfully\" }";
        int statusCode;

        var module = objectMapper.readValue(body, Module.class);
        var roleEntityOptional = getRoleByName(roleName);


        if (roleEntityOptional.isEmpty()) {
            output = String.format("{ \"message\": \"%s\" }", "Role does not exist for" + roleName);
            statusCode = 404;
        } else {
            var roleEntity = roleEntityOptional.get();
            var modules = roleEntity.getModules();
            if (LambdaUtil.isEmptyCollection(modules)) {
                modules = new ArrayList<>();
            }
            modules.add(module);
            saveRole(roleEntity);
            statusCode = 200;

        }
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(statusCode);
        return response;
    }


    private Optional<RoleEntity> getRoleByName(String roleName) {

        Map<String, AttributeValue> expressionAttributeValuesMap = new HashMap<>();
        expressionAttributeValuesMap.put(":roleValue", new AttributeValue().withS(roleName));
        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#roleName", "name");
        var getQueryExpression = new DynamoDBQueryExpression<RoleEntity>();
        var conditionExpression = "#roleName = :roleValue";
        getQueryExpression.withKeyConditionExpression(conditionExpression)
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValuesMap);
        return dynamoDBMapper.query(RoleEntity.class, getQueryExpression).stream().findAny();
    }

    public APIGatewayV2HTTPResponse deleteModule(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        var pathParams = event.getPathParameters();
        var roleName = pathParams.get("roleName");
        var body = event.getBody();
        var moduleDeleteRequest = objectMapper.readValue(body, List.class);
        String output = "{ \"message\": \"Module Deleted successfully\" }";
        int statusCode;


        var roleEntityOptional = getRoleByName(roleName);


        if (roleEntityOptional.isEmpty()) {
            output = String.format("{ \"message\": \"%s\" }", "Role does not exist for" + roleName);
            statusCode = 404;
        } else {
            var roleEntity = roleEntityOptional.get();
            var modules = roleEntity.getModules();
            var modulesToDelete =
                    modules.stream().filter(module -> moduleDeleteRequest.contains(module.getName())).collect(Collectors.toSet());
            roleEntity.getModules().removeAll(modulesToDelete);
            saveRole(roleEntity);
            statusCode = 200;
        }

        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(statusCode);
        return response;
    }

    public APIGatewayV2HTTPResponse createPermission(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        var pathParams = event.getPathParameters();
        var roleName = pathParams.get("roleName");
        var moduleName = pathParams.get("moduleName");
        var body = event.getBody();
        String output = "{ \"message\": \"Permission Added successfully\" }";
        int statusCode;

        var permissions = objectMapper.readValue(body, List.class);
        var roleEntityOptional = getRoleByName(roleName);


        if (roleEntityOptional.isEmpty()) {
            output = String.format("{ \"message\": \"%s\" }", "Role does not exist for" + roleName);
            statusCode = 404;
        } else {
            var roleEntity = roleEntityOptional.get();
            var modules = roleEntity.getModules();
            var moduleEntityOptional = modules.stream().filter(module -> module.getName().equalsIgnoreCase(moduleName)).findFirst();
            if (moduleEntityOptional.isEmpty()) {
                output = String.format("{ \"message\": \"%s\" }", "Module does not exist for" + moduleName);
                statusCode = 404;
            } else {
                var moduleEntity = moduleEntityOptional.get();
                var permissionList = moduleEntity.getPermissions();
                if (LambdaUtil.isEmptyCollection(permissionList)) {
                    permissionList = new HashSet<>();
                }
                permissionList.addAll(permissions);
                moduleEntity.setPermissions(permissionList);

                saveRole(roleEntity);
                statusCode = 200;
            }
        }
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(statusCode);
        return response;
    }

    public APIGatewayV2HTTPResponse deletePermissions(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        var pathParams = event.getPathParameters();
        var roleName = pathParams.get("roleName");
        var moduleName = pathParams.get("moduleName");
        var body = event.getBody();
        var permissionDeleteRequest = objectMapper.readValue(body, List.class);
        String output = "{ \"message\": \"Permissions Deleted successfully\" }";
        int statusCode;

        var roleEntityOptional = getRoleByName(roleName);


        if (roleEntityOptional.isEmpty()) {
            output = String.format("{ \"message\": \"%s\" }", "Role does not exist for" + roleName);
            statusCode = 404;
        } else {
            var roleEntity = roleEntityOptional.get();
            var modules = roleEntity.getModules();
            var moduleEntityOptional = modules.stream().filter(module -> module.getName().equalsIgnoreCase(moduleName)).findFirst();
            if (moduleEntityOptional.isEmpty()) {
                output = String.format("{ \"message\": \"%s\" }", "Module does not exist for" + moduleName);
                statusCode = 404;
            } else {
                var moduleEntity = moduleEntityOptional.get();
                var permissions = moduleEntity.getPermissions();
                var modulesToDelete =
                        permissions.stream().filter(permissionDeleteRequest::contains).collect(Collectors.toSet());

                permissions.removeAll(modulesToDelete);
                moduleEntity.setPermissions(permissions);
                saveRole(roleEntity);
                statusCode = 200;
            }
        }
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(statusCode);
        return response;
    }
}
