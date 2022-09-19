package com.quixteam.usersapi;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quixteam.usersapi.entity.Module;
import com.quixteam.usersapi.entity.RoleEntity;
import com.quixteam.usersapi.services.UserService;
import com.quixteam.usersapi.util.LambdaUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private DynamoDBMapper dynamoDBMapper;
    private ObjectMapper objectMapper;

    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        LambdaLogger logger = context.getLogger();
        objectMapper = new ObjectMapper();
        dynamoDBMapper = dynamoDBMapper();
        UserService userService = new UserService(objectMapper, dynamoDBMapper, cognitoClient());
        logger.log("Got event" + event.toString());
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        APIGatewayV2HTTPResponse response;
        var routeKey = event.getRouteKey();
        try {
            switch (routeKey) {
                case "POST /users":
                    response = userService.createNewUser(event);
                    break;
                case "POST /users/{username}/roles":
                    response = userService.assignRole(event);
                    break;
                case "GET /users":
                    response = userService.getAllUsers();
                    break;
                case "POST /roles":
                    response = createRole(event);
                    break;
                case "GET /roles":
                    response = getAllRoles();
                    break;
                case "POST /modules/{roleName}":
                    response = createModule(event);
                    break;
                case "POST /permissions/{roleName}/{moduleName}":
                    response = createPermission(event);
                    break;
                default:
                    response = new APIGatewayV2HTTPResponse();
                    response.setBody("No implementation found for " + event.getRouteKey());
                    response.setStatusCode(404);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            var output = String.format("{ \"message\": \"%s\" }", e.getMessage());
            response = new APIGatewayV2HTTPResponse();
            response.setBody(output);
            response.setStatusCode(400);

        }
        response.setHeaders(headers);
        return response;
    }


    private List<String> getRoleNames() {
        var roleEntities = getRoles();
        var roleNames = roleEntities.stream().map(RoleEntity::getName).collect(Collectors.toList());
        return roleNames;
    }

    private List<RoleEntity> getRoles() {

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        return dynamoDBMapper.scan(RoleEntity.class, scanExpression);
    }


    private APIGatewayV2HTTPResponse createRole(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        var body = event.getBody();
        String output = "{ \"message\": \"Role Created successfully\" }";
        int statusCode;

        var roleRequest = objectMapper.readValue(body, RoleEntity.class);
        List<String> roleNames = getRoleNames();

        if (roleNames.stream().anyMatch(role -> role.equalsIgnoreCase(roleRequest.getName()))) {
            output = String.format("{ \"message\": \"%s\" }", "Role already exist " + roleRequest.getName());
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

    private APIGatewayV2HTTPResponse getAllRoles() throws JsonProcessingException {

        var roleEntities = getRoles();
        var output = objectMapper.writeValueAsString(roleEntities);
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(200);
        return response;
    }


    private APIGatewayV2HTTPResponse createModule(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
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


    private APIGatewayV2HTTPResponse createPermission(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
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
                    permissionList = new ArrayList<>();
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

    public AWSCognitoIdentityProvider cognitoClient() {
        return AWSCognitoIdentityProviderClientBuilder.standard().withRegion(Regions.US_EAST_2).build();
    }

    public DynamoDBMapper dynamoDBMapper() {
        return new DynamoDBMapper(amazonDynamoDB(), DynamoDBMapperConfig.DEFAULT);
    }


    private AmazonDynamoDB amazonDynamoDB() {
        return AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_2)
                .build();
    }


}
