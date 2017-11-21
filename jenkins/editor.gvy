
node {
  def projectDir
  environment{
      PATH="/usr/local/rvm/rubies/jruby-9.1.13.0/bin:$PATH"
  }
   stage('Preparation') { // for display purposes
      // Get some code from a GitHub repository
      sh 'whoami;  touch fake.war; rm *.war || echo "no war files"'
      
      checkout([$class: 'GitSCM', branches: [[name: '*/master-ci-demo']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-editor']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-editor.git']]])
      
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
        sh " echo $PATH; which jruby; JAVA_OPTS='-server -d64'; jruby -S bundle install --without development test;bundle exec rake assets:clobber;PRECOMPILING_FOR_PRODUCTION='false' ;bundle exec rake assets:precompile  RAILS_ENV=production RAILS_GROUPS=assets;bundle exec warble"
        script{
            projectDir = pwd() 
            def version = new File(projectDir+"/config/version.txt")
            def war = new File(projectDir+"/nsl-editor.war")
            war.renameTo(projectDir+"/nsl#editor##${version.text.trim()}.war")
        }
        }
      }
      
      
   }
   stage("Deploy to $INVENTORY_NAME") {
      dir('nsl-infra'){
          def extra_vars = /'{"apps":[{"app": "editor"}], "war_names": [{"war_name": "nsl#editor##1.53"}   ],   "war_source_dir": "$projectDir"}'/
          sh "ansible-playbook -vvv -i inventory/$INVENTORY_NAME -u ubuntu playbooks/deploy.yml -e $extra_vars"
      }
   }
}
