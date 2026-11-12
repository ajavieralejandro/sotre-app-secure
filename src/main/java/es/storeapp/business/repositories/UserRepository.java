package es.storeapp.business.repositories;

import es.storeapp.business.entities.User;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.apache.commons.validator.routines.EmailValidator; // Librería robusta estándar

@Repository
public class UserRepository extends AbstractRepository<User> {
    
    private static final String FIND_USER_BY_EMAIL_QUERY = "SELECT u FROM User u WHERE u.email = :email";
    private static final String COUNT_USER_BY_EMAIL_QUERY = "SELECT COUNT(u) FROM User u WHERE u.email = :email";
    private static final String LOGIN_QUERY = "SELECT u FROM User u WHERE u.email = :email AND u.password = :password";

    public User findByEmail(String email) {
        // Validación rigurosa utilizando la librería externa Apache Commons Validator
        if (!EmailValidator.getInstance().isValid(email)) {
            logger.warn("Intento de consulta con email inválido detectado por Commons Validator: {}", email);
            return null;
        }
        try {
            TypedQuery<User> query = entityManager.createQuery(FIND_USER_BY_EMAIL_QUERY, User.class);
            query.setParameter("email", email);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public boolean existsUser(String email) {
        if (!EmailValidator.getInstance().isValid(email)) {
            return false;
        }
        TypedQuery<Long> query = entityManager.createQuery(COUNT_USER_BY_EMAIL_QUERY, Long.class);
        query.setParameter("email", email);
        return (query.getSingleResult() > 0);
    }
    
    public User findByEmailAndPassword(String email, String password) {
        // Segunda línea defensiva: validación con librería antes de ejecutar la consulta blindada
        if (!EmailValidator.getInstance().isValid(email)) {
            logger.warn("Intento de login con email inválido detectado por Commons Validator: {}", email);
            return null;
        }
        try {
            TypedQuery<User> query = entityManager.createQuery(LOGIN_QUERY, User.class);
            query.setParameter("email", email);
            query.setParameter("password", password);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}