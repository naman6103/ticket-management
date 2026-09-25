CREATE TABLE tickets (
    id VARCHAR(36) PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    assignee VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    category VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE comments (
    id VARCHAR(36) PRIMARY KEY,
    ticket_id VARCHAR(36) NOT NULL REFERENCES tickets (id),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_comments_ticket_id ON comments (ticket_id);
