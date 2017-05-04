--
-- Copyright (c) 2015-2017 Crisp AB
--
-- Permission is hereby granted, free of charge, to any person obtaining a copy
-- of this software and associated documentation files (the "Software"), to deal
-- in the Software without restriction, including without limitation the rights
-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
-- copies of the Software, and to permit persons to whom the Software is
-- furnished to do so, subject to the following conditions:
--
-- The above copyright notice and this permission notice shall be included in
-- all copies or substantial portions of the Software.
--
-- THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
-- THE SOFTWARE.
--

CREATE TABLE customers (
  id                BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
  name              VARCHAR(100)          NOT NULL UNIQUE,
  username          VARCHAR(100)          NOT NULL UNIQUE,
  encodedPassword   VARCHAR(80)           NULL,
  plaintextPassword VARCHAR(255)          NULL
  COMMENT 'Will be replaced by an encoded password at application startup',
  licenseKey        VARCHAR(40)           NOT NULL,
  createdAt         TIMESTAMP             NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO customers (id, name, username, plaintextPassword, licenseKey) VALUES (0, 'Codekvast Demo Customer', 'demo', 'demo', '');
