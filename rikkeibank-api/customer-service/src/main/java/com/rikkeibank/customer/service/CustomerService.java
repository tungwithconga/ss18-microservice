package com.rikkeibank.customer.service;

import com.rikkeibank.customer.dto.*;
import com.rikkeibank.customer.entity.Customer;
import com.rikkeibank.customer.exception.BusinessException;
import com.rikkeibank.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Tạo khách hàng mới
     */
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email already exists: " + request.getEmail());
        }
        Customer customer = Customer.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .nationalId(request.getNationalId())
                .address(request.getAddress())
                .dateOfBirth(request.getDateOfBirth())
                .build();
        return CustomerResponse.from(customerRepository.save(customer));
    }

    /**
     * Xem chi tiết — cache theo id (Cache-Aside)
     */
    @Cacheable(value = "customer", key = "#id")
    public CustomerResponse getById(Long id) {
        log.info("[CACHE MISS] Loading customer {} from DB", id);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Customer not found: " + id));
        return CustomerResponse.from(customer);
    }

    public List<CustomerResponse> getAll() {
        return customerRepository.findAll().stream().map(CustomerResponse::from).toList();
    }

    /**
     * Cập nhật — cập nhật cache cùng lúc (@CachePut)
     */
    @Transactional
    @CachePut(value = "customer", key = "#id")
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Customer not found: " + id));
        customer.setFullName(request.getFullName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        customer.setDateOfBirth(request.getDateOfBirth());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    /**
     * Xóa — evict cache
     */
    @Transactional
    @CacheEvict(value = "customer", key = "#id")
    public void delete(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Customer not found: " + id);
        }
        customerRepository.deleteById(id);
        log.info("[CACHE EVICT] Customer {} removed from cache", id);
    }
}
