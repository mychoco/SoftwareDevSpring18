/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package controller;

import cart.ShoppingCart;
import entity.Category;
import entity.Product;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ejb.EJB;
import session.CategoryFacade;
import session.OrderManager;
import session.ProductFacade;
import validate.Validator;

/**
 *
 * @author cinnak-T440s
 */
@WebServlet(name = "Controller", loadOnStartup = 1, urlPatterns = {
        "/category",
        "/addToCart",
        "/viewCart",
        "/updateCart",
        "/checkout",
        "/purchase",
        "/chooseLanguage"})
public class ControllerServlet extends HttpServlet {

    private String surcharge;
    
    @EJB
    private CategoryFacade categoryFacade;
    @EJB
    private ProductFacade productFacade;
    @EJB
    private OrderManager orderManager;
    
    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        
        super.init(servletConfig);
        
        surcharge = servletConfig.getServletContext().getInitParameter("deliverySurcharge");
        
        // store category list in servlet context
        getServletContext().setAttribute("categories", categoryFacade.findAll());
    }

    /**
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

        String userPath = request.getServletPath();
        HttpSession session = request.getSession();
        Category selectedCategory;
        Collection<Product> categoryProducts;

        // if category page is requested
        if (userPath.equals("/category")) {
            
            // get categoryId from request
            String categoryId = request.getQueryString();
            
            if (categoryId != null) {
                
                // get selected category
                selectedCategory = categoryFacade.find(Short.parseShort(categoryId));
                
                // place selected category in request scope
                session.setAttribute("selectedCategory", selectedCategory);
                
                // get all products for selected category
                categoryProducts = selectedCategory.getProductCollection();
            
                // place category products in request scope
                session.setAttribute("categoryProducts", categoryProducts);
            }

        // if cart page is requested
        } else if (userPath.equals("/viewCart")) {
            
            String clear = request.getParameter("clear");
            
            if ((clear != null) && clear.equals("true")) {
                
                ShoppingCart cart = (ShoppingCart) session.getAttribute("cart");
                cart.clear();
            }

            userPath = "/cart";

        // if checkout page is requested
        } else if (userPath.equals("/checkout")) {
            
            ShoppingCart cart = (ShoppingCart) session.getAttribute("cart");
            
            cart.calculateTotal(surcharge);

        // if user switches language
        } else if (userPath.equals("/chooseLanguage")) {
            
            String language = request.getParameter("language");
            
            request.setAttribute("language", language);
            
            String userView = (String) session.getAttribute("view");
            
            if ((userView != null) && (!userView.equals("/index"))) {
                userPath = userView;
            } else {
                try {
                    request.getRequestDispatcher("/index.jsp").forward(request, response);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return;
            }
        }

        // use RequestDispatcher to forward request internally
        String url = "/WEB-INF/view" + userPath + ".jsp";

        try {
            request.getRequestDispatcher(url).forward(request, response);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");

        String userPath = request.getServletPath();
        HttpSession session = request.getSession();
        ShoppingCart cart = (ShoppingCart) session.getAttribute("cart");
        Validator validator = new Validator();
        
        // if addToCart action is called
        if (userPath.equals("/addToCart")) {
            
            if (cart == null) {
                cart = new ShoppingCart();
                session.setAttribute("cart", cart);
            }
            
            String productId = request.getParameter("productId");
            
            if (!productId.isEmpty()) {
                
                Product product = productFacade.find(Integer.parseInt(productId));
                cart.addItem(product);
            }
            
            userPath = "/category";

        // if updateCart action is called
        } else if (userPath.equals("/updateCart")) {
            
            String productId = request.getParameter("productId");
            String quantity = request.getParameter("quantity");
            
            boolean invalidEntry = validator.validateQuantity(productId, quantity);
            
            if (!invalidEntry) {
                
                Product product = productFacade.find(Integer.parseInt(productId));
                cart.update(product, quantity);   
            }
            
            userPath = "/cart";

        // if purchase action is called
        } else if (userPath.equals("/purchase")) {
            if (cart != null) {
                
                String name = request.getParameter("name");
                String email = request.getParameter("email");
                String phone = request.getParameter("phone");
                String address = request.getParameter("address");
                String cityRegion = request.getParameter("cityRegion");
                String ccNumber = request.getParameter("creditcard");
                
                boolean validationErrorFlag = false;
                validationErrorFlag = validator.validateForm(name, email, phone, address, cityRegion, ccNumber, request);
                
                if (validationErrorFlag == true) {
                    request.setAttribute("validationErrorFlag", validationErrorFlag);
                    userPath = "/checkout";
                } else {
                    
                    int orderId = orderManager.placeOrder(name, email, phone, address, cityRegion, ccNumber, cart);
                    
                    if (orderId != 0) {
                        
                        Locale locale = (Locale) session.getAttribute("javax.servlet.jsp.jstl.fmt.locale.session");
                        String language = "";
                        
                        if (locale != null) {
                            
                            language = (String) locale.getLanguage();
                        }
                        
                        cart = null;
                        
                        session.invalidate();
                        
                        if (!language.isEmpty()) {
                            
                            request.setAttribute("language", language);
                        }
                        
                        Map orderMap = orderManager.getOrderDetails(orderId);
                        
                        request.setAttribute("customer", orderMap.get("customer"));
                        request.setAttribute("products", orderMap.get("products"));
                        request.setAttribute("orderRecord", orderMap.get("orderRecord"));
                        request.setAttribute("orderedProducts", orderMap.get("orderedProducts"));
                        
                        userPath = "/confirmation";
                        
                    } else {
                        userPath = "/checkout";
                        request.setAttribute("orderFailureFlag", true);
                    }
                }                
            }
        }

        // use RequestDispatcher to forward request internally
        String url = "/WEB-INF/view" + userPath + ".jsp";

        try {
            request.getRequestDispatcher(url).forward(request, response);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
}
