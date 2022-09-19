package com.quixteam.usersapi.services;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.SignUpRequest;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quixteam.usersapi.entity.RoleEntity;
import com.quixteam.usersapi.entity.UserEntity;
import com.quixteam.usersapi.requestbody.CreateUserRequest;
import com.quixteam.usersapi.util.LambdaUtil;

import java.util.*;
import java.util.stream.Collectors;

public class UserService {
    private ObjectMapper objectMapper;
    private DynamoDBMapper dynamoDBMapper;
    private AWSCognitoIdentityProvider awsCognitoIdentityProvider;

    public UserService(ObjectMapper objectMapper, DynamoDBMapper dynamoDBMapper, AWSCognitoIdentityProvider awsCognitoIdentityProvider) {
        this.objectMapper = objectMapper;
        this.dynamoDBMapper = dynamoDBMapper;
        this.awsCognitoIdentityProvider = awsCognitoIdentityProvider;

    }

    public APIGatewayV2HTTPResponse createNewUser(APIGatewayV2HTTPEvent event)
            throws JsonProcessingException {

        var body = event.getBody();
        String output = "{ \"message\": \"User Saved successfully\" }";
        var userRequest = objectMapper.readValue(body, CreateUserRequest.class);
        List<String> roleNames = getRoleNames();

        if (userRequest.getRoles().stream().anyMatch(role -> !roleNames.contains(role))) {
            output = String.format("{ \"message\": \"%s\" }", "Invalid roles provided " + userRequest.getRoles());
        } else {
            saveUserInCognito(userRequest);
            saveUser(userRequest);
        }


        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(200);
        return response;
    }

    private List<String> getRoleNames() {
        var roleEntities = getRoles();
        return roleEntities.stream().map(RoleEntity::getName).collect(Collectors.toList());
    }

    private List<RoleEntity> getRoles() {

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        return dynamoDBMapper.scan(RoleEntity.class, scanExpression);
    }


    private void saveUserInCognito(CreateUserRequest userRequest) {
        var cognitoClient = this.awsCognitoIdentityProvider;
        SignUpRequest signUpRequest = new SignUpRequest();
        signUpRequest.setUsername(userRequest.getUsername());
        signUpRequest.setPassword(userRequest.getPassword());
        AttributeType attributeType = new AttributeType();
        attributeType.setName("email");
        attributeType.setValue(userRequest.getEmail());
        signUpRequest.setUserAttributes(List.of(attributeType));
        signUpRequest.setClientId("5b0of4v9vle7nek3l98ht3arc");
        cognitoClient.signUp(signUpRequest);
    }

    private void saveUser(CreateUserRequest userRequest) {
        var userEntity = new UserEntity();
        userEntity.setUsername(userRequest.getUsername());
        userEntity.setRoles(userRequest.getRoles());
        dynamoDBMapper.save(userEntity);
    }

    public APIGatewayV2HTTPResponse getAllUsers() throws JsonProcessingException {
        var userEntities = getUsers();
        var output = objectMapper.writeValueAsString(userEntities);
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(200);
        return response;

    }

    private List<UserEntity> getUsers() {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        return dynamoDBMapper.scan(UserEntity.class, scanExpression);
    }


    public APIGatewayV2HTTPResponse assignRole(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        var body = event.getBody();
        var pathParams = event.getPathParameters();
        var username = pathParams.get("username");

        String output = "{ \"message\": \"User Updated successfully\" }";
        int statusCode = 200;
        var userEntityOptional = getUserByUsername(username);

        if (userEntityOptional.isEmpty()) {
            output = String.format("{ \"message\": \"%s\" }", "User not found for  " + username);
            statusCode = 404;
        } else {
            var roleRequest = objectMapper.readValue(body, List.class);
            List<String> roleNames = getRoleNames();

            if (roleNames.stream().anyMatch(role -> !roleNames.contains(role))) {
                output = String.format("{ \"message\": \"%s\" }", "Invalid roles provided " + roleRequest);
            } else {
                var userEntity = userEntityOptional.get();
                var existingRoles = userEntity.getRoles();
                if (LambdaUtil.isEmptyCollection(existingRoles)) {
                    existingRoles = new ArrayList<>();
                }
                existingRoles.addAll(roleRequest);
                userEntity.setRoles(existingRoles);
                dynamoDBMapper.save(userEntity);
            }
        }
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output);
        response.setStatusCode(statusCode);
        return response;
    }


    private Optional<UserEntity> getUserByUsername(String username) {

        Map<String, AttributeValue> expressionAttributeValuesMap = new HashMap<>();
        expressionAttributeValuesMap.put(":username", new AttributeValue().withS(username));
        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#username", "username");
        var getQueryExpression = new DynamoDBQueryExpression<UserEntity>();
        var conditionExpression = "#username = :username";
        getQueryExpression.withKeyConditionExpression(conditionExpression)
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValuesMap);
        return dynamoDBMapper.query(UserEntity.class, getQueryExpression).stream().findAny();
    }

}