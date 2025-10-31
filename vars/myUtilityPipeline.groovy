def call(Map config = [:]) {
    pipeline {
        agent any
        
        stages {
            stage('Docker Cleanup') {
                steps {
                    echo "🧹 Cleaning up Docker resources..."
                    script {
                        // Remove dangling images
                        sh 'docker image prune -f'
                        
                        // Remove unused containers
                        sh 'docker container prune -f'
                        
                        // Remove unused volumes
                        if (config.removeVolumes == true) {
                            sh 'docker volume prune -f'
                        }
                        
                        // Remove unused networks
                        sh 'docker network prune -f'
                        
                        echo "✅ Docker cleanup completed"
                    }
                }
            }
            
            stage('System Info') {
                steps {
                    echo "📊 System Information:"
                    sh '''
                        echo "=== Docker Info ==="
                        docker --version
                        docker info --format "{{.ServerVersion}}"
                        
                        echo "=== Disk Usage ==="
                        df -h
                        
                        echo "=== Memory Usage ==="
                        free -h
                        
                        echo "=== Running Containers ==="
                        docker ps --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
                    '''
                }
            }
        }
        
        post {
            always {
                echo "🏁 Utility pipeline completed!"
            }
        }
    }
}