package org.jboss.as.testsuite.integration.secman.servlets;

import javax.management.MBeanServerFactory;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(MBeanPermissionServlet.SERVLET_PATH)
public class MBeanPermissionServlet extends HttpServlet {

    public static final String SERVLET_PATH = "/MbeanPropServlet";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");
        System.out.println("Hello MBeanPermissionServlet");

        MBeanServerFactory.createMBeanServer();
        System.out.println("Hello created");
        resp.setStatus(200);
        final PrintWriter writer = resp.getWriter();
        writer.write("OK");
        writer.close();
    }
}
