-- StockPro Database Initialisation Script
-- Use this once against AWS RDS, or let the optional local MySQL profile run it.
-- MySQL is the primary relational store per service.

CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS stockpro_product;
CREATE DATABASE IF NOT EXISTS stockpro_warehouse_db;
CREATE DATABASE IF NOT EXISTS stockpro_purchase_db;
CREATE DATABASE IF NOT EXISTS stockpro_payment_db;
CREATE DATABASE IF NOT EXISTS stockpro_supplier;
CREATE DATABASE IF NOT EXISTS stockpro_movements;
CREATE DATABASE IF NOT EXISTS stockpro_analytics_db;
CREATE DATABASE IF NOT EXISTS stockpro_alert_db;
