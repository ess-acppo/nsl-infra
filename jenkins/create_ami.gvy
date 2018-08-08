/*

*/

stage("Creating AMI") {
    node{
        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
        dir('nsl-infra'){
            sh 'whoami'
            sh 'echo "ANSIBLE VERSION :" && ansible --version'
            sh 'echo "PYTHON VERSION :" && python --version'
            sh 'echo "JAVA VERSION :" && java --version'
            def extra_vars = /'shard_type=$SHARD_TYPE'/
            sh "packer build -var $extra_vars packer-template.json "

        }
    }
}