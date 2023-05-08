 java -cp fabrikt-7.3.1.jar com.cjbooms.fabrikt.cli.CodeGen \
             --output-directory './tøst/' \
             --base-package 'com.example' \
             --api-file './spec/test.yaml' \
             --targets 'http_models' \
             --http-client-opts resilience4j