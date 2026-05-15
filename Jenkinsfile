def serviceDirectories() {
    return [
        'eureka-service',
        'api-gateway',
        'authservice',
        'product-service',
        'warehouse-service',
        'purchase-service',
        'payment-service',
        'supplier-service',
        'stockmovement-services',
        'analytics-service',
        'alert-service'
    ]
}

def parseSelectedServices(String scope, String selectedServices) {
    def allServices = serviceDirectories()

    if (scope == 'all') {
        return allServices
    }

    def requested = selectedServices
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    if (requested.isEmpty()) {
        error('SERVICE_SCOPE is selected, but SERVICES is empty.')
    }

    def invalid = requested.findAll { !allServices.contains(it) }
    if (!invalid.isEmpty()) {
        error("Invalid service name(s): ${invalid.join(', ')}. Valid services: ${allServices.join(', ')}")
    }

    return requested.unique()
}

def dockerImageName(String namespace, String service) {
    return "${namespace}/stockpro-${service}"
}

def shellQuote(String value) {
    return "'${value.replace("'", "'\"'\"'")}'"
}

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    parameters {
        choice(
            name: 'PIPELINE_ACTION',
            choices: ['build_push_only'],
            description: 'Build services and push Docker Hub images. EC2 deployment is manual.'
        )
        choice(
            name: 'SERVICE_SCOPE',
            choices: ['all', 'selected'],
            description: 'Build/deploy the complete application or only selected services.'
        )
        string(
            name: 'SERVICES',
            defaultValue: 'authservice,product-service',
            description: 'Comma-separated services when SERVICE_SCOPE=selected.'
        )
        string(
            name: 'DOCKERHUB_NAMESPACE',
            defaultValue: '',
            description: 'Docker Hub username or organization, for example vikashkumar.'
        )
        string(
            name: 'DOCKERHUB_CREDENTIALS_ID',
            defaultValue: 'dockerhub-credentials',
            description: 'Jenkins username/password credential ID for Docker Hub.'
        )
        string(
            name: 'IMAGE_TAG',
            defaultValue: 'latest',
            description: 'Image tag to deploy. Use latest for normal deployments.'
        )
        string(
            name: 'RDS_ENDPOINT',
            defaultValue: 'stockpro-db.cv2k06sm0z9c.ap-south-1.rds.amazonaws.com',
            description: 'AWS RDS endpoint used by Docker Compose.'
        )
        string(
            name: 'RDS_PORT',
            defaultValue: '3306',
            description: 'AWS RDS MySQL port.'
        )
        string(
            name: 'RDS_USERNAME',
            defaultValue: 'admin',
            description: 'AWS RDS username.'
        )
        password(
            name: 'RDS_PASSWORD',
            defaultValue: '',
            description: 'AWS RDS password. Required for deploy actions.'
        )
        password(
            name: 'JWT_SECRET',
            defaultValue: '',
            description: 'JWT signing secret for generated runtime .env.'
        )
        string(
            name: 'GOOGLE_CLIENT_ID',
            defaultValue: '',
            description: 'Google OAuth client ID for generated runtime .env.'
        )
        password(
            name: 'GOOGLE_CLIENT_SECRET',
            defaultValue: '',
            description: 'Google OAuth client secret for generated runtime .env.'
        )
        string(
            name: 'STOCKPRO_MAIL_USERNAME',
            defaultValue: '',
            description: 'SMTP username for generated runtime .env.'
        )
        password(
            name: 'STOCKPRO_MAIL_PASSWORD',
            defaultValue: '',
            description: 'SMTP app password for generated runtime .env.'
        )
        string(
            name: 'STOCKPRO_ALERT_EMAIL_TO',
            defaultValue: '',
            description: 'Alert notification email recipient for generated runtime .env.'
        )
        string(
            name: 'RAZORPAY_KEY_ID',
            defaultValue: '',
            description: 'Razorpay key ID for generated runtime .env.'
        )
        password(
            name: 'RAZORPAY_KEY_SECRET',
            defaultValue: '',
            description: 'Razorpay key secret for generated runtime .env.'
        )
        booleanParam(
            name: 'RUN_TESTS',
            defaultValue: false,
            description: 'Run Maven tests before Docker image build. Default is false for faster CI builds.'
        )
        booleanParam(
            name: 'PUSH_LATEST_TAG',
            defaultValue: true,
            description: 'Also push/update the latest tag when IMAGE_TAG is not latest.'
        )
    }

    environment {
        COMPOSE_PROJECT_NAME = 'stockpro'
        SELECTED_SERVICES_FILE = '.jenkins-selected-services'
    }

    stages {
        stage('Prepare') {
            steps {
                script {
                    if (!params.DOCKERHUB_NAMESPACE?.trim()) {
                        error('DOCKERHUB_NAMESPACE is required.')
                    }

                    def selected = parseSelectedServices(params.SERVICE_SCOPE, params.SERVICES)
                    env.SELECTED_SERVICES = selected.join(' ')
                    env.DOCKERHUB_NAMESPACE_VALUE = params.DOCKERHUB_NAMESPACE.trim()
                    env.IMAGE_TAG_VALUE = params.IMAGE_TAG.trim() ?: 'latest'
                    env.RDS_ENDPOINT_VALUE = params.RDS_ENDPOINT.trim()
                    env.RDS_PORT_VALUE = params.RDS_PORT.trim() ?: '3306'
                    env.RDS_USERNAME_VALUE = params.RDS_USERNAME.trim()
                    env.RDS_PASSWORD_VALUE = params.RDS_PASSWORD
                    writeFile(file: env.SELECTED_SERVICES_FILE, text: selected.join('\n') + '\n')

                    echo "Pipeline action: ${params.PIPELINE_ACTION}"
                    echo "Selected services: ${env.SELECTED_SERVICES}"
                    echo "Docker Hub namespace: ${env.DOCKERHUB_NAMESPACE_VALUE}"
                    echo "Image tag: ${env.IMAGE_TAG_VALUE}"
                }
            }
        }

        stage('Prepare Runtime Env Files') {
            steps {
                script {
                    if (!env.RDS_ENDPOINT_VALUE?.trim()) {
                        error('RDS_ENDPOINT is required.')
                    }
                    if (!env.RDS_USERNAME_VALUE?.trim()) {
                        error('RDS_USERNAME is required.')
                    }
                    if (!env.RDS_PASSWORD_VALUE?.trim()) {
                        error('RDS_PASSWORD is required.')
                    }

                    writeFile(
                        file: '.env',
                        text: """RDS_ENDPOINT=${env.RDS_ENDPOINT_VALUE}
RDS_PORT=${env.RDS_PORT_VALUE}
RDS_USERNAME=${env.RDS_USERNAME_VALUE}
RDS_PASSWORD=${env.RDS_PASSWORD_VALUE}
MYSQL_ROOT_PASSWORD=${env.RDS_PASSWORD_VALUE}
MYSQL_DATABASE=auth_db

JWT_SECRET=${params.JWT_SECRET}
JWT_EXPIRATION=28800000

SERVER_FORWARD_HEADERS_STRATEGY=framework
SERVER_TOMCAT_THREADS_MAX=50
SERVER_TOMCAT_THREADS_MIN_SPARE=5

SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.MySQLDialect
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=true
SPRING_JPA_GENERATE_DDL=true
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect
SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=true

EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-service:8761/eureka/
EUREKA_CLIENT_REGISTER_WITH_EUREKA=true
EUREKA_CLIENT_FETCH_REGISTRY=true
EUREKA_INSTANCE_PREFER_IP_ADDRESS=true

MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=*
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always
MANAGEMENT_ENDPOINT_SHUTDOWN_ENABLED=true
MANAGEMENT_ENDPOINTS_WEB_BASE_PATH=/actuator
MANAGEMENT_HEALTH_MAIL_ENABLED=false

LOGGING_LEVEL_ORG_SPRINGFRAMEWORK=INFO
LOGGING_LEVEL_COM_STOCKPRO=DEBUG

INFO_APP_NAME=StockPro Service
INFO_APP_VERSION=1.0
INFO_APP_DESCRIPTION=StockPro microservice

SPRINGDOC_API_DOCS_PATH=/v3/api-docs
SPRINGDOC_SWAGGER_UI_PATH=/swagger-ui.html
SPRINGDOC_SWAGGER_UI_ENABLED=true

GOOGLE_CLIENT_ID=${params.GOOGLE_CLIENT_ID}
GOOGLE_CLIENT_SECRET=${params.GOOGLE_CLIENT_SECRET}
GOOGLE_CLIENT_SCOPE=profile,email
STOCKPRO_FRONTEND_OAUTH2_SUCCESS_URL=http://localhost:4200/oauth2/callback
STOCKPRO_OTP_EXPIRY_MINUTES=10

STOCKPRO_MAIL_ENABLED=true
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
STOCKPRO_MAIL_USERNAME=${params.STOCKPRO_MAIL_USERNAME}
STOCKPRO_MAIL_PASSWORD=${params.STOCKPRO_MAIL_PASSWORD}
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_SMTP_STARTTLS_REQUIRED=true
SPRING_MAIL_SMTP_SSL_TRUST=smtp.gmail.com
SPRING_MAIL_SMTP_CONNECTION_TIMEOUT=5000
SPRING_MAIL_SMTP_TIMEOUT=5000
SPRING_MAIL_SMTP_WRITE_TIMEOUT=5000

SPRING_RABBITMQ_HOST=rabbitmq
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest
SPRING_RABBITMQ_LISTENER_SIMPLE_MISSING_QUEUES_FATAL=false
SPRING_RABBITMQ_LISTENER_SIMPLE_RETRY_ENABLED=true
SPRING_RABBITMQ_LISTENER_SIMPLE_RETRY_INITIAL_INTERVAL=3000
SPRING_RABBITMQ_LISTENER_SIMPLE_RETRY_MAX_ATTEMPTS=5
ANALYTICS_QUEUE_STOCK_MOVEMENT=stock.movement.queue
ANALYTICS_EXCHANGE=stockpro.exchange

STOCKPRO_INTERNAL_SERVICE_TOKEN=stockpro-internal-token
STOCKPRO_ALERT_LOW_STOCK_THRESHOLD=20
STOCKPRO_ALERT_EMAIL_TO=${params.STOCKPRO_ALERT_EMAIL_TO}
STOCKPRO_FRONTEND_DASHBOARD_URL=http://localhost:4200/dashboard

PAYMENT_SERVICE_BASE_URL=http://payment-service:8089/api/v1/payments
PURCHASE_SERVICE_BASE_URL=http://purchase-service:8084/api/v1/purchase-orders
PURCHASE_NOTIFY_ENABLED=true
WAREHOUSE_SERVICE_BASE_URL=http://warehouse-service:8083/api/v1/warehouses

FEIGN_CLIENT_CONFIG_DEFAULT_CONNECT_TIMEOUT=5000
FEIGN_CLIENT_CONFIG_DEFAULT_READ_TIMEOUT=5000

RAZORPAY_KEY_ID=${params.RAZORPAY_KEY_ID}
RAZORPAY_KEY_SECRET=${params.RAZORPAY_KEY_SECRET}

SPRING_CACHE_TYPE=simple
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_TIMEOUT=2s
STOCKPRO_CACHE_PRODUCT_TTL_MINUTES=30
STOCKPRO_CACHE_SUPPLIER_TTL_MINUTES=30

GATEWAY_CORS_ALLOWED_ORIGIN_1=http://localhost:4200
GATEWAY_CORS_ALLOWED_ORIGIN_2=http://localhost:4201
GATEWAY_ROUTE_AUTH_URI=lb://AUTHSERVICE
GATEWAY_ROUTE_PRODUCT_URI=lb://PRODUCT-SERVICE
GATEWAY_ROUTE_WAREHOUSE_URI=lb://WAREHOUSE-SERVICE
GATEWAY_ROUTE_SUPPLIER_URI=lb://SUPPLIER-SERVICE
GATEWAY_ROUTE_MOVEMENT_URI=lb://stockmovement-services
GATEWAY_ROUTE_PURCHASE_URI=lb://purchase-service
GATEWAY_ROUTE_PAYMENT_URI=lb://payment-service
GATEWAY_ROUTE_ANALYTICS_URI=lb://analytics-service
GATEWAY_ROUTE_ALERT_URI=lb://ALERT-SERVICE
"""
                    )
                }
            }
        }

        stage('Build and Test Services') {
            steps {
                script {
                    def goals = params.RUN_TESTS ? 'clean test package' : 'clean package -DskipTests'
                    readFile(env.SELECTED_SERVICES_FILE).split('\n').findAll { it.trim() }.each { service ->
                        dir(service.trim()) {
                            sh """
                                set -eu
                                if [ ! -f ./mvnw ]; then
                                  cp ../eureka-service/mvnw ./mvnw
                                  cp -R ../eureka-service/.mvn ./.mvn
                                fi
                                chmod +x ./mvnw
                                ./mvnw -B ${goals}
                            """
                        }
                    }
                }
            }
        }

        stage('Validate Compose') {
            steps {
                sh '''
                    set -eu
                    DOCKERHUB_NAMESPACE="$DOCKERHUB_NAMESPACE_VALUE" IMAGE_TAG="$IMAGE_TAG_VALUE" \
                      docker compose -f docker-compose.yml -f docker-compose.prod.yml config >/dev/null
                '''
            }
        }

        stage('Build and Push Docker Images') {
            steps {
                script {
                    withCredentials([usernamePassword(
                        credentialsId: params.DOCKERHUB_CREDENTIALS_ID,
                        usernameVariable: 'DOCKERHUB_USERNAME',
                        passwordVariable: 'DOCKERHUB_PASSWORD'
                    )]) {
                        sh '''
                            set -eu
                            printf '%s' "$DOCKERHUB_PASSWORD" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
                        '''

                        readFile(env.SELECTED_SERVICES_FILE).split('\n').findAll { it.trim() }.each { serviceName ->
                            def service = serviceName.trim()
                            def image = dockerImageName(env.DOCKERHUB_NAMESPACE_VALUE, service)

                            sh """
                                set -eu
                                docker build -t ${shellQuote("${image}:${env.IMAGE_TAG_VALUE}")} ${shellQuote(service)}
                            """

                            sh "docker push ${shellQuote("${image}:${env.IMAGE_TAG_VALUE}")}"

                            if (params.PUSH_LATEST_TAG && env.IMAGE_TAG_VALUE != 'latest') {
                                sh """
                                    set -eu
                                    docker tag ${shellQuote("${image}:${env.IMAGE_TAG_VALUE}")} ${shellQuote("${image}:latest")}
                                    docker push ${shellQuote("${image}:latest")}
                                """
                            }
                        }

                        sh 'docker logout'
                    }
                }
            }
        }
    }

    post {
        success {
            echo "StockPro CI/CD completed successfully for: ${env.SELECTED_SERVICES}"
        }
        failure {
            echo 'StockPro CI/CD failed. Check the stage logs above for the exact command/error.'
        }
        always {
            deleteDir()
        }
    }
}
