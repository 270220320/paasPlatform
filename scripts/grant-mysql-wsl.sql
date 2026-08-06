-- Allow WSL / LAN clients to use MySQL on Windows.
-- Run on Windows (MySQL 本机)，例如：
--   mysql -uroot -pmeng1234 < scripts/grant-mysql-wsl.sql
-- 或在 MySQL Workbench / Navicat 中执行。

CREATE DATABASE IF NOT EXISTS UAVPlatform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- root 从任意主机（含 WSL eth0）登录
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'meng1234';
ALTER USER 'root'@'%' IDENTIFIED BY 'meng1234';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;

-- 兼容只认网段的场景
CREATE USER IF NOT EXISTS 'root'@'172.28.%' IDENTIFIED BY 'meng1234';
ALTER USER 'root'@'172.28.%' IDENTIFIED BY 'meng1234';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'172.28.%' WITH GRANT OPTION;

FLUSH PRIVILEGES;
