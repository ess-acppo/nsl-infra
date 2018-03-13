
node {
  def projectDir
  environment{
      PATH="/usr/local/rvm/rubies/jruby-9.1.13.0/bin:$PATH"
  }
   stage('Preparation') { // for display purposes
      // Get some code from a GitHub repository
      sh 'whoami;  touch fake.war; rm *.war || echo "no war files"'
      
      checkout([$class: 'GitSCM', branches: [[name: '*/prePR']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-editor']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-editor.git']]])
      
      checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
    
     
   }
   /*stage('Unit test') {
        
        dir('nsl-editor'){
            try{
                sh 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64;$WORKSPACE/services/grailsw clean-all;$WORKSPACE/services/grailsw "test-app unit:"'
        
            }catch(e) {
                currentBuild.result = 'failure'
            }
           }
      
   }*/
   stage('Building war') {
      withEnv(['PATH+=/opt/jruby-9.1.13.0/bin']){
        dir('nsl-editor'){
        sh " echo $PATH; which jruby; JAVA_OPTS='-server -d64'; jruby -S bundle install --without development test;bundle exec rake assets:clobber;bundle exec rake assets:precompile  RAILS_ENV=production RAILS_GROUPS=assets;bundle exec warble"
        script{
            projectDir = pwd() 
            def version = new File(projectDir+"/config/version.txt")
            def war = new File(projectDir+"/nsl-editor.war")
            war.renameTo(projectDir+"/nxl#editor##${version.text.trim()}.war")
        }
        }
      }
      
      
   }
  stage("Deploy") {
      def shard_vars = '@shard_vars/$SHARD_TYPE.json'
      dir('nsl-infra'){
          if (ENVIRONMENT_NAME) {
              def env_instance_name = "$ENVIRONMENT_NAME".split(",")[0]
              def env_name = env_instance_name.split("-")[0]
              def elb_dns = "$SHARD_TYPE"+"."+"$env_name"+".oztaxa.com"
              def extra_vars = /'{"elb_dns": "elb_dns","nxl_env_name":"ENVIRONMENT_NAME","apps":[{"app": "editor"}], "war_names": [{"war_name": "nxl#editor##1.53"}   ],   "war_source_dir": "$projectDir"}'/
              sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$ENVIRONMENT_NAME/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars -e $shard_vars"
          }else if (INVENTORY_NAME){
              def extra_vars = /'{"nxl_env_name":"ENVIRONMENT_NAME","apps":[{"app": "editor"}], "war_names": [{"war_name": "nxl#editor##1.53"}   ],   "war_source_dir": "$projectDir"}'/
              sh "ansible-playbook -vvv -i inventory/$INVENTORY_NAME -u ubuntu playbooks/deploy.yml -e $extra_vars -e $shard_vars"
          }
      }
   }
}
