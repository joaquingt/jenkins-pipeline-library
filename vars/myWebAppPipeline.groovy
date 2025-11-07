def call(Map config = [:]) {
    pipeline {
        agent {
            docker {
                image 'mylocal/jenkins-agent:latest'
                label 'docker-agent'
            } 
        }
        environment {
            APP_NAME = "${config.appName ?: 'my-first-app'}"
            DOCKER_PORT = "${config.dockerPort ?: '3001'}"
            IMAGE_TAG = "${env.BUILD_NUMBER}"
        }
        
        stages {
            stage('Setup Docker') {
                steps {
                    script {
                        // Check if docker is available
                        def dockerExists = sh(script: 'which docker', returnStatus: true) == 0
                        
                        if (!dockerExists) {
                        echo "📦 Docker not installed"
                        }
                        
                        // Verify Docker is working
                        sh 'docker --version'
                        echo "✅ Docker CLI available"
                    }
                }
            }
            
            stage('Checkout') {
                steps {
                    echo "🔄 Checking out code for ${env.APP_NAME}..."
                    script {
                        echo "Repository: ${env.GIT_URL ?: 'Local'}"
                        echo "Branch: ${env.GIT_BRANCH ?: 'Unknown'}"
                        echo "Commit: ${env.GIT_COMMIT ?: 'Unknown'}"
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
                        
                        // Build image using shell command instead of docker.build()
                        sh "whoami"
                        sh "docker build -t ${imageName} ."
                        echo "✅ Built image: ${imageName}"
                        
                        // Tag as latest
                        sh "docker tag ${imageName} ${env.APP_NAME}:latest"
                        echo "✅ Tagged as: ${env.APP_NAME}:latest"
                        
                        // List images to verify
                        sh "docker images ${env.APP_NAME}"
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
                        
                        // Show running containers
                        sh "docker ps | grep ${env.APP_NAME} || echo 'Container not found in ps'"
                    }
                }
            }
            
            stage('Health Check') {
                steps {
                    echo "🏥 Performing health check..."
                    script {
                        echo "⏳ Waiting for container to start..."
                        sleep(10) // Wait for container to start
                        
                        // Check if container is running
                        def containerRunning = sh(
                            script: "docker ps --filter 'name=${env.APP_NAME}' --filter 'status=running' --quiet",
                            returnStdout: true
                        ).trim()
                        
                        if (containerRunning) {
                            echo "✅ Container is running: ${containerRunning}"
                            
                            // Try health check with curl
                            def controllerIp = InetAddress.localHost.hostAddress
                            def agentIp = sh(script: "hostname -I | awk '{print \$1}'", returnStdout: true).trim()                            
                            def healthCheck = sh(
                                script: "curl -f http://${controllerIp}:${env.DOCKER_PORT} || exit 1",
                                returnStatus: true
                            )
                            
                            if (healthCheck == 0) {
                                echo "✅ Health check passed!"
                            } else {
                                echo "⚠️ Health check failed, but container is running"
                                echo "🔍 Container logs:"
                                sh "docker logs ${env.APP_NAME} || true"
                                echo "Controller IP: ${controllerIp}"
                                echo "Agent IP: ${agentIp}"
                            }
                        } else {
                            echo "❌ Container is not running"
                            sh "docker logs ${env.APP_NAME} || true"
                            error("Container failed to start")
                        }
                    }
                }
            }
        }
        
        post {
            always {
                echo "🏁 Pipeline completed for ${env.APP_NAME}!"
                script {
                    // Clean up old images (keep last 3) - simplified version
                    try {
                        sh """
                            # Get image IDs for this app (excluding 'latest' tag)
                            OLD_IMAGES=\$(docker images ${env.APP_NAME} --format "{{.ID}} {{.Tag}}" | grep -E '[0-9]+' | sort -k2 -nr | tail -n +4 | cut -d' ' -f1)
                            if [ ! -z "\$OLD_IMAGES" ]; then
                                echo "🧹 Cleaning up old images: \$OLD_IMAGES"
                                docker rmi \$OLD_IMAGES || true
                            else
                                echo "🧹 No old images to clean up"
                            fi
                        """
                    } catch (Exception e) {
                        echo "⚠️ Could not clean up old images: ${e.getMessage()}"
                    }
                }
            }
            success {
                echo "✅ Pipeline succeeded!"
                script {
                    currentBuild.description = "✅ Deployed ${env.APP_NAME}:${env.IMAGE_TAG} on port ${env.DOCKER_PORT}"
                    sh "docker stop ${env.APP_NAME} || true"
                    sh "docker rm ${env.APP_NAME} || true"
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
        }
    }
 }
