DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_trading_portal_dev') THEN
    CREATE ROLE app_trading_portal_dev LOGIN PASSWORD 'oINpHv9h8fKB0jJl6tUM2Zgd';
  ELSE
    ALTER ROLE app_trading_portal_dev WITH PASSWORD 'oINpHv9h8fKB0jJl6tUM2Zgd';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_trading_portal_preprod') THEN
    CREATE ROLE app_trading_portal_preprod LOGIN PASSWORD '0ngKVFA2MdHQOzeJhk7SDYq5';
  ELSE
    ALTER ROLE app_trading_portal_preprod WITH PASSWORD '0ngKVFA2MdHQOzeJhk7SDYq5';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_trading_portal_prod') THEN
    CREATE ROLE app_trading_portal_prod LOGIN PASSWORD 'H8DILqusrhlmFiNyUKJvOMAW';
  ELSE
    ALTER ROLE app_trading_portal_prod WITH PASSWORD 'H8DILqusrhlmFiNyUKJvOMAW';
  END IF;
END
$$;
CREATE SCHEMA IF NOT EXISTS dev AUTHORIZATION app_trading_portal_dev;
CREATE SCHEMA IF NOT EXISTS preprod AUTHORIZATION app_trading_portal_preprod;
CREATE SCHEMA IF NOT EXISTS prod AUTHORIZATION app_trading_portal_prod;
GRANT CONNECT ON DATABASE app_trading_portal TO app_trading_portal_dev, app_trading_portal_preprod, app_trading_portal_prod;
GRANT USAGE, CREATE ON SCHEMA dev TO app_trading_portal_dev;
GRANT USAGE, CREATE ON SCHEMA preprod TO app_trading_portal_preprod;
GRANT USAGE, CREATE ON SCHEMA prod TO app_trading_portal_prod;
ALTER ROLE app_trading_portal_dev SET search_path TO dev;
ALTER ROLE app_trading_portal_preprod SET search_path TO preprod;
ALTER ROLE app_trading_portal_prod SET search_path TO prod;
