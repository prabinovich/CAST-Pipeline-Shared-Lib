// vars/buildPlugin.groovy
def call (Map config){

	node ('master') {
	    stage ('CAST-Get Code') {
	        dir('CAST-Scripts') {
	           git branch: 'master', credentialsId: 'Github-prabinovich', url: 'https://github.com/prabinovich/CAST-Jenkins-Pipeline.git'
	        }
	        dir('App-Code') {
	           git credentialsId: 'Github-prabinovich', url: 'https://github.com/prabinovich/WebStore.git'
	        } 
	    }
	    
	    stage ('CAST-Assessment Model') {
	    	try {
	        	echo '-- Enable Assessment Model --'
	        	bat '"%WORKSPACE%\\CAST-Scripts\\CLI-Scripts\\CMS_ImportAssessmentModel.bat" "profile=sandbox838" "app=${config.appname}" "filepath=%WORKSPACE%\\CAST-Scripts\\QualityModels\\CAST 8.3.8 Assessment Model - Standard.pmx"'
	    	}
	    	catch (err) {
	    		echo '*** Assessment model import failed ***'
	    		echo err
	    		echo '**************************************'
	    	}
	    }
	    
	    stage ('CAST-Packaging') {
	        echo '-- Packaging and Delivery of Source Code --'
	        bat '"%WORKSPACE%\\CAST-Scripts\\CLI-Scripts\\CMS_AutomateDelivery.bat" "profile=sandbox838" "app=${config.appname}" "fromVersion=Baseline" "version=version %BUILD_NUMBER%"'
	    } 
	    
	    stage ('CAST-Analysis') {
	        echo '-- Analyze Application --'
	        bat '"%WORKSPACE%\\CAST-Scripts\\CLI-Scripts\\CMS_Analyze.bat" "profile=sandbox838" "app=${config.appname}"'
	    }
	    
	    stage ('CAST-Snapshot') {
	        echo '-- Generate Snapshot --'
	        bat '"%WORKSPACE%\\CAST-Scripts\\CLI-Scripts\\CMS_GenerateSnapshot.bat" "profile=sandbox838" "app=${config.appname}" "version=version %BUILD_NUMBER%"'
	    }
	    
	    stage('CAST-Publish Results'){
	        echo "-- Consolidate Snapshot --"        
	        withCredentials([usernamePassword(credentialsId: 'CAST-DB-Keys', passwordVariable: 'PWD1', usernameVariable: 'USR1')]) {
	            bat '"%WORKSPACE%\\CAST-Scripts\\CLI-Scripts\\AAD_ConsolidateSnapshot.bat" "measure=sandbox838_measure" "central=sandbox838_central" "password=%PWD1%"'
	        }
	        
	        withCredentials([usernamePassword(credentialsId: 'CAST-Dashboard-Keys', passwordVariable: 'PWD1', usernameVariable: 'USR1')]) {
	            bat "curl.exe -u ${USR1}:${PWD1} -H \"Accept: application/json\" http://localhost:8080/CAST-Health-Engineering-838/rest/server/reload"
	        }
	    }
	    
	    stage('CAST-Publish HTML Report'){
	 		echo "-- Create CAST Report in Jenkins --"    
	    	dir('CAST-Report') {
	    		withCredentials([usernamePassword(credentialsId: 'CAST-Dashboard-Keys', passwordVariable: 'PWD1', usernameVariable: 'USR1')]) {
	    			bat 'python "%WORKSPACE%\\CAST-Scripts\\RestAPI\\CAST-Results-Report.py" --connection=http://localhost:8080/CAST-Health-Engineering-838/rest --username=%USR1% --password=%PWD1% --appname=${config.appname}'
	    		}
	    	}
	    		publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'CAST-Report', reportFiles: 'index.html', reportName: 'CAST Analysis Report', reportTitles: ''])
	    }
	    
	    stage('CAST-Check Success'){
	    	echo "-- Quality Gate - Check Analysis Results --"
	    	withCredentials([usernamePassword(credentialsId: 'CAST-Dashboard-Keys', passwordVariable: 'PWD1', usernameVariable: 'USR1')]) {
	    		bat 'python "%WORKSPACE%\\CAST-Scripts\\RestAPI\\CAST-Check-Rule.py" --connection=http://localhost:8080/CAST-Health-Engineering-838/rest --username=%USR1% --password=%PWD1% --appname=${config.appname} --ruleid=7742'
	    	}
	    }
	}
}
