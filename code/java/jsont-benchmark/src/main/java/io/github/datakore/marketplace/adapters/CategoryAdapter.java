package io.github.datakore.marketplace.adapters;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.marketplace.entity.Category;

public class CategoryAdapter implements SchemaAdapter<Category> {
    @Override
    public Class<Category> logicalType() {
        return Category.class;
    }

    @Override
    public Category createTarget() {
        return new Category();
    }

    @Override
    public void set(Object target, String fieldName, Object value) {
        Category category = (Category) target;
        switch (fieldName) {
            case "categoryId":
                category.setCategoryId((String) value);
                break;
            case "categoryName":
                category.setCategoryName((String) value);
                break;
            case "department":
                category.setDepartment((String) value);
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        Category category = (Category) target;
        switch (fieldName) {
            case "categoryId":
                return category.getCategoryId();
            case "categoryName":
                return category.getCategoryName();
            case "department":
                return category.getDepartment();
            default:
                return null;
        }
    }
}
