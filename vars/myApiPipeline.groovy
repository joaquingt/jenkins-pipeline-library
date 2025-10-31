def call(Map config = [:]) {
    pipeline {
        agent any
        
        environment {
            API_NAME = config.apiName ?: 'my-api'
            API_PORT = config.apiPort ?: '3000'
            IMAGE_TAG = "${env.BUILD_NUMBER}"
        }
        
        stages {
            stage('Checkout') {
                steps {
                    echo "🔄 Checking out API code for ${env.API_NAME}..."
                }
            }
            
            stage('Install Dependencies') {
                steps {
                    echo "📦 Installing dependencies..."
                    script {
                        if (fileExists('package.json')) {
                            sh 'npm install'
                        } else if (fileExists('requirements.txt')) {
                            sh 'pip install -r requirements.txt'
                        } else if (fileExists('go.mod')) {
                            sh 'go mod download'
                        }
                    }
                }
            }
            
            stage('Test') {
                steps {
                    echo "🧪 Running API tests..."
                    script {
                        if (fileExists('package.json')) {
                            sh 'npm test'
                        } else if (fileExists('pytest.ini') || fileExists('requirements.txt')) {
                            sh 'pytest'
                        } else if (fileExists('go.mod')) {
                            sh 'go test ./...'
                        }
                    }
                }
            }
            
            stage('Build') {
                steps {
                    echo "🐳 Building API Docker image..."
                    script {
                        def imageName = "${env.API_NAME}:${env.IMAGE_TAG}"
                        docker.build(imageName)
                        echo "✅ Built image: ${imageName}"
                    }
                }
            }
            
            stage('Deploy') {
                steps {
                    echo "🚀 Deploying API..."
                    script {
                        sh "docker stop ${env.API_NAME} || true"
                        sh "docker rm ${env.API_NAME} || true"
                        sh """
                            docker run -d \
                                --name ${env.API_NAME} \
                                -p ${env.API_PORT}:8080 \
                                ${env.API_NAME}:${env.IMAGE_TAG}
                        """
                        echo "✅ API deployed on port ${env.API_PORT}"
                    }
                }
            }
        }
        
        post {
            always {
                echo "🏁 API Pipeline completed!"
            }
            success {
                echo "✅ API Pipeline succeeded!"
                script {
                    currentBuild.description = "✅ Deployed ${env.API_NAME}:${env.IMAGE_TAG}"
                }
            }
            failure {
                echo "❌ API Pipeline failed!"
            }
        }
    }
}