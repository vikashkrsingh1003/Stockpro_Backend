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
"""
                    )

                    serviceDirectories().each { service ->
                        def envFile = "${service}/.env"
                        if (!fileExists(envFile)) {
                            writeFile(file: envFile, text: "# Generated by Jenkins for Docker Compose env_file compatibility.\n")
                        }
                    }
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
