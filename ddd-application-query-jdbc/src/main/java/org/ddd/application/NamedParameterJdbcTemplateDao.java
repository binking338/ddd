package org.ddd.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author qiaohe
 * @date 2024/1/1
 */
@Slf4j
public class NamedParameterJdbcTemplateDao {
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public NamedParameterJdbcTemplateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private static <T> Map<String, Object> convertToPropertiesMap(Map<String, Object> resultMap, T object) {
        resultMap = resultMap == null
                ? new HashMap<>()
                : resultMap;

        if (object == null) {
            return resultMap;
        }
        if ((Map.class).isAssignableFrom(object.getClass())) {
            resultMap.putAll((Map<String, ?>) object);
        } else {
            BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(object);

            for (Field propertyDescriptor : wrapper.getWrappedClass().getDeclaredFields()) {
                String fieldName = propertyDescriptor.getName();

                try {
                    Object value = wrapper.getPropertyValue(fieldName);
                    resultMap.put(fieldName, value);
                } catch (Exception e) {
                    // 处理获取属性值时发生的异常
                    log.error("命名参数转换异常 fieldName=" + fieldName, e);
                }
            }
        }

        return resultMap;
    }

    /**
     * 查询一个实体，数据异常返回0或1条记录将会抛出异常
     *
     * @param entityClass
     * @param sql
     * @param paramBeans
     * @param <E>
     * @return
     */
    public <E> E queryOne(Class<E> entityClass, String sql, Object... paramBeans) {
        HashMap<String, Object> params = new HashMap<>();
        for (Object paramBean : paramBeans) {
            convertToPropertiesMap(params, paramBean);
        }
        E result = this.namedParameterJdbcTemplate.queryForObject(sql, params, (RowMapper<E>) new BeanPropertyRowMapper(entityClass));
        return result;
    }

    /**
     * 查询第一条实体记录
     *
     * @param entityClass
     * @param sql
     * @param paramBeans
     * @param <E>
     * @return
     */
    public <E> Optional<E> queryFirst(Class<E> entityClass, String sql, Object... paramBeans) {
        HashMap<String, Object> params = new HashMap<>();
        for (Object paramBean : paramBeans) {
            convertToPropertiesMap(params, paramBean);
        }
        Pattern limitPattern = Pattern.compile("\\s+LIMIT\\s+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = limitPattern.matcher(sql);
        if(!matcher.find()){
            if(sql.trim().endsWith(";")){
                sql = sql.replaceFirst(";\\s*$", " limit 1");
            } else {
                sql += " limit 1";
            }
        }
        List<E> result = this.namedParameterJdbcTemplate.query(sql, params, (RowMapper<E>) new BeanPropertyRowMapper(entityClass));
        return result.stream().findFirst();
    }

    /**
     * 查询实体列表
     *
     * @param entityClass
     * @param sql
     * @param paramBeans
     * @param <E>
     * @return
     */
    public <E> List<E> queryList(Class<E> entityClass, String sql, Object... paramBeans) {
        HashMap<String, Object> params = new HashMap<>();
        for (Object paramBean : paramBeans) {
            convertToPropertiesMap(params, paramBean);
        }
        List<E> result = this.namedParameterJdbcTemplate.query(sql, params, (RowMapper<E>) new BeanPropertyRowMapper(entityClass));
        return result;
    }

    /**
     * 返回查询计数
     *
     * @param sql
     * @param paramBeans
     * @return
     */
    public long count(String sql, Object... paramBeans) {
        HashMap<String, Object> params = new HashMap<>();
        for (Object paramBean : paramBeans) {
            convertToPropertiesMap(params, paramBean);
        }
        long total = this.namedParameterJdbcTemplate.queryForObject(sql, params, Long.class).longValue();
        return total;
    }

}
