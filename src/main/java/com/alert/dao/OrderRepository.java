package com.alert.dao;

import com.alert.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Order findById(long id);

    @Query("select o from Order o where o.completed = false")
    List<Order> findOpenOrder();
}
