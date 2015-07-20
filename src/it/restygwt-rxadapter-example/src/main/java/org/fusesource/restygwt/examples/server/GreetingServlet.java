package org.fusesource.restygwt.examples.server;

import java.io.IOException;
import java.io.StringWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.fusesource.restygwt.client.Resource;

public class GreetingServlet extends HttpServlet {
    private static final String helloWorldJson = "[{\"greeting\":\"Hello World\"}]";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        System.out.println("Sending Hello World");
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode helloJsonNode = mapper.readTree(helloWorldJson);
            mapper.writeValue(resp.getOutputStream(), helloJsonNode);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            System.out.flush();
            System.err.flush();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        System.out.println("Creating custom greeting.");
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode nameObject = mapper.readValue(req.getInputStream(), ObjectNode.class);
            String name = nameObject.get("str").asText();

            String greeting = "Hello " + name;
            ObjectNode resultObject = new ObjectNode(JsonNodeFactory.instance);
            resultObject.put("str", greeting);
            mapper.writeValue(resp.getOutputStream(), resultObject);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            System.out.flush();
            System.err.flush();
        }
    }
}
