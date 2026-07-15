-- Insert default users with SHA-256 hashed passwords

-- Insert user 'postgres' with password 'postgres'
INSERT INTO users (username, password) 
VALUES ('postgres', 'qUKzfM+vWoE7FDLKogmkO50UTketDeFUnCicJT5VbNU=');

-- Insert user 'test' with password 'test'
INSERT INTO users (username, password) 
VALUES ('test', 'n4bQgYhMfWWaL+qgxVrQFaO/TxsrC4Is0V1sFbDwCgg=');
