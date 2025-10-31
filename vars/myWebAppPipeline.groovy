def call(Map config = [:]) {
    pipeline {
        agent any
        
        environment {
            APP_NAME = config.appName ?: 'my-first-app'
            DOCKER_PORT = config.dockerPort ?: '3001'
            IMAGE_TAG = "${env.BUILD_NUMBER}"
        }
        
        stages {
            stage('Checkout') {
                steps {
                    echo "🔄 Checking out code for ${env.APP_NAME}..."
                    // Code is automatically checked out in declarative pipeline
                    script {
                        echo "Repository: ${env.GIT_URL}"
                        echo "Branch: ${env.GIT_BRANCH}"
                        echo "Commit: ${env.GIT_COMMIT}"
                    }
                }
            }
            
            stage('Validate') {
                steps {
                    echo "✅ Validating project structure..."
                    script {
                        def requiredFiles = ['index.html', 'test.sh', 'Dockerfile']
                        requiredFiles.each { file ->
                            if (!fileExists(file)) {
                                error("❌ Required file missing: ${file}")
                            } else {
                                echo "✅ Found: ${file}"
                            }
                        }
                    }
                }
            }
            
            stage('Test') {
                steps {
                    echo "🧪 Running tests..."
                    sh 'chmod +x test.sh'
                    sh './test.sh'
                }
            }
            
            stage('Build Docker Image') {
                steps {
                    echo "🐳 Building Docker image..."
                    script {
                        def imageName = "${env.APP_NAME}:${env.IMAGE_TAG}"
                        def image = docker.build(imageName)
                        echo "✅ Built image: ${imageName}"
                        
                        // Tag as latest
                        sh "docker tag ${imageName} ${env.APP_NAME}:latest"
                        echo "✅ Tagged as: ${env.APP_NAME}:latest"
                    }
                }
            }
            
            stage('Deploy') {
                steps {
                    echo "🚀 Deploying application..."
                    script {
                        // Stop and remove existing container if running
                        sh """
                            docker stop ${env.APP_NAME} || true
                            docker rm ${env.APP_NAME} || true
                        """
                        
                        // Run new container
                        sh """
                            docker run -d \
                                --name ${env.APP_NAME} \
                                -p ${env.DOCKER_PORT}:80 \
                                ${env.APP_NAME}:${env.IMAGE_TAG}
                        """
                        
                        echo "✅ Application deployed and running on port ${env.DOCKER_PORT}"
                        echo "🌐 Access at: http://localhost:${env.DOCKER_PORT}"
                    }
                }
            }
            
            stage('Health Check') {
                steps {
                    echo "🏥 Performing health check..."
                    script {
                        sleep(5) // Wait for container to start
                        
                        def healthCheck = sh(
                            script: "curl -f http://localhost:${env.DOCKER_PORT} || exit 1",
                            returnStatus: true
                        )
                        
                        if (healthCheck == 0) {
                            echo "✅ Health check passed!"
                        } else {
                            error("❌ Health check failed!")
                        }
                    }
                }
            }
        }
        
        post {
            always {
                echo "🏁 Pipeline completed for ${env.APP_NAME}!"
                script {
                    // Clean up old images (keep last 3)
                    sh """
                        docker images ${env.APP_NAME} --format "table {{.Tag}}" | grep -E '^[0-9]+\$' | sort -nr | tail -n +4 | xargs -r docker rmi ${env.APP_NAME}: || true
                    """
                }
            }
            success {
                echo "✅ Pipeline succeeded!"
                script {
                    currentBuild.description = "✅ Deployed ${env.APP_NAME}:${env.IMAGE_TAG} on port ${env.DOCKER_PORT}"
                }
            }
            failure {
                echo "❌ Pipeline failed!"
                script {
                    currentBuild.description = "❌ Failed to deploy ${env.APP_NAME}"
                    // Clean up failed container
                    sh "docker stop ${env.APP_NAME} || true"
                    sh "docker rm ${env.APP_NAME} || true"
                }
            }
            unstable {
                echo "⚠️ Pipeline unstable!"
            }
        }
    }
}