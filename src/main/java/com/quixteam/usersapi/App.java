package com.quixteam.usersapi;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quixteam.usersapi.services.RoleService;
import com.quixteam.usersapi.services.UserService;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        LambdaLogger logger = context.getLogger();
        var objectMapper = new ObjectMapper();
        var dynamoDBMapper = dynamoDBMapper();
        UserService userService = new UserService(objectMapper, dynamoDBMapper, cognitoClient());
        RoleService roleService = new RoleService(objectMapper, dynamoDBMapper);
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
                    response = roleService.createRole(event);
                    break;
                case "PUT /roles/{roleName}":
                    response = roleService.updateRole(event);
                    break;
                case "GET /roles":
                    response = roleService.getAllRoles();
                    break;
                case "POST /modules/{roleName}":
                    response = roleService.createModule(event);
                    break;
                case "DELETE /modules/{roleName}":
                    response = roleService.deleteModule(event);
                    break;
                case "POST /permissions/{roleName}/{moduleName}":
                    response = roleService.createPermission(event);
                    break;
                case "DELETE /permissions/{roleName}/{moduleName}":
                    response = roleService.deletePermissions(event);
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
