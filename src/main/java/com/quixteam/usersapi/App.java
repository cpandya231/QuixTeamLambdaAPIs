package com.quixteam.usersapi;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.SignUpRequest;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quixteam.usersapi.entity.RoleEntity;
import com.quixteam.usersapi.entity.UserEntity;
import com.quixteam.usersapi.requestbody.CreateUserRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        logger.log("Got event" + event.toString());
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        APIGatewayV2HTTPResponse response;
        var routeKey = event.getRouteKey();
        try {
            switch (routeKey) {
                case "POST /users":
                    response = createNewUser(event);
                    break;
                case "GET /users":
                    response = getAllUsers();
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

    private APIGatewayV2HTTPResponse createNewUser(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        var body = event.getBody();
        System.out.println("Got body " + body);
        String output = "{ \"message\": \"User Saved successfully\" }";

        dynamoDBMapper = dynamoDBMapper();
        var userRequest = objectMapper.readValue(body, CreateUserRequest.class);
        var roleEntities = getRoles();
        var roleNames = roleEntities.stream().map(RoleEntity::getName).collect(Collectors.toList());

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

    private List<RoleEntity> getRoles() {

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        return dynamoDBMapper.scan(RoleEntity.class, scanExpression);
    }

    private void saveUser(CreateUserRequest userRequest) {
        var userEntity = new UserEntity();
        userEntity.setUsername(userRequest.getUsername());
        userEntity.setRoles(userRequest.getRoles());
        dynamoDBMapper.save(userEntity);
    }

    private void saveUserInCognito(CreateUserRequest userRequest) {
        var cognitoClient = cognitoClient();
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

    private APIGatewayV2HTTPResponse getAllUsers() throws JsonProcessingException {
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
