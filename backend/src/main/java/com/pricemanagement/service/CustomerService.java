package com.pricemanagement.service;

import com.pricemanagement.entity.Customer;
import com.pricemanagement.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public List<Customer> getActiveCustomers() {
        return customerRepository.findByStatusOrderBySortOrderAsc(Customer.CustomerStatus.ACTIVE);
    }

    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }

    public Optional<Customer> getCustomerByCode(String code) {
        return customerRepository.findByCode(code);
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        if (customerRepository.existsByCode(customer.getCode())) {
            throw new IllegalArgumentException("客户编码已存在: " + customer.getCode());
        }
        Customer savedCustomer = customerRepository.save(customer);
        log.info("Created customer: {}", savedCustomer.getName());
        return savedCustomer;
    }

    @Transactional
    public Customer updateCustomer(Long id, Customer customer) {
        Customer existingCustomer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在: " + id));

        if (customer.getName() != null) {
            existingCustomer.setName(customer.getName());
        }
        if (customer.getCode() != null && !customer.getCode().equals(existingCustomer.getCode())) {
            if (customerRepository.existsByCode(customer.getCode())) {
                throw new IllegalArgumentException("客户编码已存在: " + customer.getCode());
            }
            existingCustomer.setCode(customer.getCode());
        }
        if (customer.getContact() != null) {
            existingCustomer.setContact(customer.getContact());
        }
        if (customer.getPhone() != null) {
            existingCustomer.setPhone(customer.getPhone());
        }
        if (customer.getAddress() != null) {
            existingCustomer.setAddress(customer.getAddress());
        }
        if (customer.getStatus() != null) {
            existingCustomer.setStatus(customer.getStatus());
        }
        if (customer.getSortOrder() != null) {
            existingCustomer.setSortOrder(customer.getSortOrder());
        }
        if (customer.getRemark() != null) {
            existingCustomer.setRemark(customer.getRemark());
        }

        Customer savedCustomer = customerRepository.save(existingCustomer);
        log.info("Updated customer: {}", savedCustomer.getName());
        return savedCustomer;
    }

    @Transactional
    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new IllegalArgumentException("客户不存在: " + id);
        }
        customerRepository.deleteById(id);
        log.info("Deleted customer with id: {}", id);
    }

    public boolean existsByCode(String code) {
        return customerRepository.existsByCode(code);
    }
}
