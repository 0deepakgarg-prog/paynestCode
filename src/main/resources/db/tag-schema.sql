CREATE TABLE IF NOT EXISTS tenant_movii.categories (
    category_id BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL UNIQUE,
    category_name VARCHAR(100) NOT NULL,
    description TEXT,
    status TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenant_movii.tag_types (
    tag_type_id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(50) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL,
    description TEXT,
    status TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tags (
    tag_id BIGSERIAL PRIMARY KEY,
    tag_code VARCHAR(50) UNIQUE NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    tag_type VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenant_movii.account_tags (
    id BIGSERIAL PRIMARY KEY,
    account_id TEXT NOT NULL,
    tag_id BIGINT NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    CONSTRAINT fk_user_tags_user FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_user_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(tag_id)
);
