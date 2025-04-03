# Build
custom_build(
    ref = 'order-service',
    command = './mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=$EXPECTED_REF -DskipTests'
    deps = ['pom.xml', 'src']
    )

k8s_yaml(['k8s/deployment.yml', 'k8s/service.yml'])

k8s_resource('order-service', port-forwards=['9002'])