package com.sparta.point_system.controller;

import com.sparta.point_system.entity.Product;
import com.sparta.point_system.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @PostMapping("/product")
    public Product createProduct(@RequestBody(required = false) Map<String, Object> requestBody,
                                @RequestParam(required = false) String name,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false, defaultValue = "0") Integer stock,
                                @RequestParam(required = false) String description) {
        Product product = new Product();
        
        // JSON 요청 본문이 있으면 우선 사용
        if (requestBody != null) {
            product.setName((String) requestBody.getOrDefault("name", ""));
            Object priceObj = requestBody.get("price");
            if (priceObj != null) {
                if (priceObj instanceof Number) {
                    product.setPrice(BigDecimal.valueOf(((Number) priceObj).doubleValue()));
                } else if (priceObj instanceof String) {
                    product.setPrice(new BigDecimal((String) priceObj));
                }
            }
            Object stockObj = requestBody.get("stock");
            if (stockObj != null) {
                product.setStock(stockObj instanceof Number ? ((Number) stockObj).intValue() : Integer.parseInt(stockObj.toString()));
            }
            product.setDescription((String) requestBody.getOrDefault("description", ""));
        } else {
            // 기존 방식 (RequestParam) 지원
            product.setName(name != null ? name : "");
            product.setPrice(price != null ? price : BigDecimal.ZERO);
            product.setStock(stock != null ? stock : 0);
            product.setDescription(description);
        }
        
        return productRepository.save(product);
    }

    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/product/{productId}")
    public Optional<Product> getProductById(@PathVariable Long productId) {
        return productRepository.findById(productId);
    }

    @PutMapping("/product/{productId}")
    public Product updateProduct(@PathVariable Long productId,
                                @RequestParam(required = false) String name,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false) Integer stock,
                                @RequestParam(required = false) String description) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            if (name != null) product.setName(name);
            if (price != null) product.setPrice(price);
            if (stock != null) product.setStock(stock);
            if (description != null) product.setDescription(description);
            return productRepository.save(product);
        }
        throw new RuntimeException("Product not found with id: " + productId);
    }

    @DeleteMapping("/product/{productId}")
    public String deleteProduct(@PathVariable Long productId) {
        productRepository.deleteById(productId);
        return "Product deleted successfully";
    }
}

