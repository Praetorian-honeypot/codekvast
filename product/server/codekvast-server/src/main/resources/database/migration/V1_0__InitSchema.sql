-- Roles ----------------------------------------------------------------------------------------------------
DROP TABLE IF EXISTS roles;
CREATE TABLE roles (
  name VARCHAR(20) NOT NULL UNIQUE,
);

-- Users ----------------------------------------------------------------------------------------------------
DROP TABLE IF EXISTS users;
CREATE TABLE users (
  id                 INTEGER                             NOT NULL IDENTITY,
  username           VARCHAR(100)                        NOT NULL UNIQUE,
  encoded_password   VARCHAR(80),
  plaintext_password VARCHAR(255),
  enabled            BOOLEAN DEFAULT TRUE                NOT NULL,
  email_address VARCHAR(64) UNIQUE,
  full_name          VARCHAR(255),
  created_at    TIMESTAMP DEFAULT current_timestamp NOT NULL,
  modified_at   TIMESTAMP AS NOW()
);

DROP TABLE IF EXISTS user_roles;
CREATE TABLE user_roles (
  user_id     INTEGER                             NOT NULL REFERENCES users (id),
  role        VARCHAR(20)                         NOT NULL REFERENCES roles (name),
  created_at TIMESTAMP DEFAULT current_timestamp NOT NULL,
  modified_at TIMESTAMP AS NOW()
);

DROP INDEX IF EXISTS ix_user_roles;
CREATE UNIQUE INDEX ix_user_roles ON user_roles (user_id, role);

-- Organisations --------------------------------------------------------------------------------------------
DROP TABLE IF EXISTS organisations;
CREATE TABLE organisations (
  id          INTEGER                             NOT NULL IDENTITY,
  name       VARCHAR(100)                        NOT NULL,
  name_lc    VARCHAR(100) AS LOWER(name),
  created_at TIMESTAMP DEFAULT current_timestamp NOT NULL,
  modified_at TIMESTAMP AS NOW()
);

DROP INDEX IF EXISTS ix_organisation_name_lc;
CREATE UNIQUE INDEX ix_organisation_name_lc ON organisations (name_lc);

DROP TABLE IF EXISTS organisation_members;
CREATE TABLE organisation_members (
  organisation_id INTEGER                             NOT NULL REFERENCES organisations (id),
  user_id         INTEGER                             NOT NULL REFERENCES users (id),
  primary_contact BOOLEAN DEFAULT FALSE               NOT NULL,
  created_at      TIMESTAMP DEFAULT current_timestamp NOT NULL,
  modified_at     TIMESTAMP AS NOW()
);

DROP INDEX IF EXISTS ix_organisation_members;
CREATE UNIQUE INDEX ix_organisation_members ON organisation_members (organisation_id, user_id);

-- Applications ---------------------------------------------------------------------------------------------
DROP TABLE IF EXISTS applications;
CREATE TABLE applications (
  id              INTEGER                             NOT NULL IDENTITY,
  organisation_id INTEGER                             NOT NULL REFERENCES organisations (id),
  name            VARCHAR(100)                        NOT NULL,
  created_at      TIMESTAMP DEFAULT current_timestamp NOT NULL,
  modified_at     TIMESTAMP AS NOW()
);

DROP INDEX IF EXISTS ix_applications;
CREATE UNIQUE INDEX ix_applications ON applications (organisation_id, name);

-- JVM runs -------------------------------------------------------------------------------------------------
DROP TABLE IF EXISTS jvm_runs;
CREATE TABLE jvm_runs (
  id                  INTEGER      NOT NULL IDENTITY,
  organisation_id     INTEGER      NOT NULL REFERENCES organisations (id),
  application_id      INTEGER      NOT NULL REFERENCES applications (id),
  application_version VARCHAR(100) NOT NULL,
  computer_id VARCHAR(50) NOT NULL,
  host_name           VARCHAR(255) NOT NULL,
  jvm_fingerprint     VARCHAR(50)  NOT NULL,
  codekvast_version   VARCHAR(20)  NOT NULL,
  codekvast_vcs_id    VARCHAR(50)  NOT NULL,
  started_at          BIGINT       NOT NULL,
  dumped_at           BIGINT       NOT NULL
);

DROP INDEX IF EXISTS ix_jvm_runs;
CREATE UNIQUE INDEX ix_jvm_runs ON jvm_runs (organisation_id, application_id, jvm_fingerprint);

-- Signatures -----------------------------------------------------------------------------------------------
DROP TABLE IF EXISTS signatures;
CREATE TABLE signatures (
  id              INTEGER       NOT NULL IDENTITY,
  organisation_id INTEGER       NOT NULL REFERENCES organisations (id),
  application_id  INTEGER       NOT NULL REFERENCES applications (id),
  jvm_id     INTEGER NOT NULL REFERENCES jvm_runs (id),
  signature       VARCHAR(2000) NOT NULL,
  invoked_at BIGINT  NOT NULL,
  confidence      TINYINT
);

DROP INDEX IF EXISTS ix_signatures_invoked_at;
CREATE INDEX ix_signatures_invoked_at ON signatures (organisation_id, invoked_at);

-- System data ----------------------------------------------------------------------------------------------
INSERT INTO roles (name) VALUES ('SUPERUSER');
INSERT INTO roles (name) VALUES ('AGENT');
INSERT INTO roles (name) VALUES ('ADMIN');
INSERT INTO roles (name) VALUES ('USER');
INSERT INTO roles (name) VALUES ('MONITOR');

-- System account ---------------------------------------------------------------------------
INSERT INTO organisations (id, name) VALUES (0, 'System');

INSERT INTO users (id, username, plaintext_password, enabled) VALUES (0, 'system', '0000', TRUE);
INSERT INTO organisation_members (organisation_id, user_id) VALUES (0, 0);
INSERT INTO user_roles (user_id, role) VALUES (0, 'SUPERUSER');
INSERT INTO user_roles (user_id, role) VALUES (0, 'AGENT');
INSERT INTO user_roles (user_id, role) VALUES (0, 'ADMIN');
INSERT INTO user_roles (user_id, role) VALUES (0, 'USER');
INSERT INTO user_roles (user_id, role) VALUES (0, 'MONITOR');

INSERT INTO users (id, username, plaintext_password, enabled) VALUES (1, 'monitor', '0000', TRUE);
INSERT INTO organisation_members (organisation_id, user_id) VALUES (0, 1);
INSERT INTO user_roles (user_id, role) VALUES (1, 'USER');
INSERT INTO user_roles (user_id, role) VALUES (1, 'MONITOR');

-- Demo account ---------------------------------------------------------------------------
INSERT INTO organisations (id, name) VALUES (1, 'Demo');

INSERT INTO users (id, username, plaintext_password, enabled) VALUES (2, 'agent', '0000', TRUE);
INSERT INTO organisation_members (organisation_id, user_id) VALUES (1, 2);
INSERT INTO user_roles (user_id, role) VALUES (2, 'AGENT');

INSERT INTO users (id, username, plaintext_password, enabled) VALUES (3, 'admin', '0000', TRUE);
INSERT INTO organisation_members (organisation_id, user_id) VALUES (1, 3);
INSERT INTO user_roles (user_id, role) VALUES (3, 'ADMIN');
INSERT INTO user_roles (user_id, role) VALUES (3, 'USER');

INSERT INTO users (id, username, plaintext_password, enabled, email_address) VALUES (4, 'user', '0000', TRUE, 'user@demo.com');
INSERT INTO organisation_members (organisation_id, user_id) VALUES (1, 4);
INSERT INTO user_roles (user_id, role) VALUES (4, 'USER');
