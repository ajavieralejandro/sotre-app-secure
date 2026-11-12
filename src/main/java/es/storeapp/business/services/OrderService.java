package es.storeapp.business.services;

import es.storeapp.business.entities.CreditCard;
import es.storeapp.business.entities.Order;
import es.storeapp.business.entities.OrderLine;
import es.storeapp.business.entities.OrderState;
import es.storeapp.business.entities.Product;
import es.storeapp.business.entities.User;
import es.storeapp.business.exceptions.InstanceNotFoundException;
import es.storeapp.business.exceptions.InvalidStateException;
import es.storeapp.business.repositories.OrderLineRepository;
import es.storeapp.business.repositories.OrderRepository;
import es.storeapp.business.repositories.ProductRepository;
import es.storeapp.business.repositories.UserRepository;
import es.storeapp.business.utils.ExceptionGenerationUtils;
import es.storeapp.common.Constants;
import java.text.MessageFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderLineRepository orderLineRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    ExceptionGenerationUtils exceptionGenerationUtils;
    
    @Transactional()
    public Order create(User user, String name, String address, List<Long> products) 
            throws InstanceNotFoundException {
        
        Order order = new Order();
        order.setName(name);
        order.setUser(user);
        order.setAddress(address != null ? address : user.getAddress());
        order.setState(OrderState.PENDING);
        order.setTimestamp(System.currentTimeMillis());
        
        // 1. CÁLCULO SEGURO DEL PRECIO EN EL SERVIDOR
        int calculatedTotalPrice = 0;
        
        // Primero creamos la orden para obtener su ID y asociar las líneas
        orderRepository.create(order);
        
        for (Long productId : products) {
            Product product = productRepository.findById(productId);
            if (product == null) {
                throw new InstanceNotFoundException(productId, String.valueOf(productId), Product.class.getName());
            }
            
            product.setSales(product.getSales() + 1);
            
            OrderLine orderLine = new OrderLine();
            orderLine.setPrice(product.getPrice()); // Precio real extraído de la BD del producto
            orderLine.setProduct(product);
            orderLine.setOrder(order);  
            orderLineRepository.create(orderLine);
            
            // Acumulamos el precio real
            calculatedTotalPrice += product.getPrice();
        }
        
        // Asignamos el precio calculado de forma autoritativa por el servidor
        order.setPrice(calculatedTotalPrice);
        orderRepository.update(order);
        
        return orderRepository.findById(order.getOrderId());
    }
    
    @Transactional()
    public Order pay(User user, Long orderId, String creditCart, Integer cvv, 
                     Integer expirationMonth, Integer expirationYear, Boolean setAsDefault) 
            throws InstanceNotFoundException, InvalidStateException {
        
        Order order = orderRepository.findById(orderId);
        
        // 2. VALIDACIÓN DE CONTROL DE ACCESO (PREVENCIÓN DE IDOR)
        // Aseguramos estrictamente que la orden pertenezca al usuario que intenta pagarla
        if (order.getUser() == null || !order.getUser().getUserId().equals(user.getUserId())) {
            logger.warn("Intento de IDOR detectado: Usuario {} intentó pagar la orden {} de otro usuario.", 
                    user.getEmail(), orderId);
            throw new InvalidStateException("No tienes permisos para acceder o modificar esta orden.");
        }

        if(order.getState() != OrderState.PENDING) {
            if(logger.isWarnEnabled()) {
                logger.warn(MessageFormat.format("Trying to pay the order {0}", order));
            }
            throw exceptionGenerationUtils.toInvalidStateException(Constants.INVALID_STATE_EXCEPTION_MESSAGE);
        }
        
        order.setState(OrderState.COMPLETED);
        
        if(setAsDefault != null && setAsDefault) {
            CreditCard card = new CreditCard();
            card.setCard(creditCart);
            card.setCvv(cvv);
            card.setExpirationMonth(expirationMonth);
            card.setExpirationYear(expirationYear);
            user.setCard(card);
            userRepository.update(user);
        }
        return orderRepository.update(order);
    }
    
    @Transactional()
    public Order cancel(User user, Long orderId) 
            throws InstanceNotFoundException, InvalidStateException {
        
        Order order = orderRepository.findById(orderId);
        
        // Validación IDOR también en la cancelación por seguridad
        if (order.getUser() == null || !order.getUser().getUserId().equals(user.getUserId())) {
            throw new InvalidStateException("No tienes permisos para cancelar esta orden.");
        }

        if(order.getState() != OrderState.PENDING) {
            throw exceptionGenerationUtils.toInvalidStateException(Constants.INVALID_STATE_EXCEPTION_MESSAGE);
        }
        order.setState(OrderState.CANCELLED);
        return orderRepository.update(order);
    }
    
    @Transactional(readOnly = true)
    public List<Order> findByUserById(Long userId) throws InstanceNotFoundException {
        User user = userRepository.findById(userId);
        if(logger.isDebugEnabled()) {
            logger.debug(MessageFormat.format("Searching the orders of the user {0}", user.getEmail()));
        }
        return orderRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) throws InstanceNotFoundException {
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public boolean findIfUserBuyProduct(Long userId, Long productId) throws InstanceNotFoundException {
        User user = userRepository.findById(userId);
        if(logger.isDebugEnabled()) {
            logger.debug(MessageFormat.format("Checking if user {0} buy the product {1}", 
                user.getEmail(), productId));
        }
        return orderLineRepository.findIfUserBuyProduct(userId, productId);
    }
}