module.exports = function(grunt) {
	
	var config = {
		rootDir: ".",
		interfaceGenPath: '_interface_name_generator/interface_name_grator.jar',
		jarsDir: grunt.option('jarsDir') || './../JARS',
		interfaceNamesFile: 'interface-names.txt',
		outputFilePath: '_interface_name_generator/out/',
		astParserDir: '_ast_parser',
		bindingsFileName: 'bindings.txt',
		outDir: 'DIST',
		relativeOutDir: grunt.option('outDir') || './../DIST',
		userCode: 'JSCODE',
		relativeUserCode: grunt.option('inputDir') || './../JSCODE'
	}
	
	grunt.initConfig({
		exec: {
			generateInterfaceNameList: {
				cmd: 'java -jar ' + config.interfaceGenPath + ' ' + config.jarsDir + ' ' + config.outputFilePath + config.interfaceNamesFile,
				cwd: '.'
			},
			npmInstallInParseProject: {
				cmd: 'npm install',
				cwd: './_ast_parser'
			},
			extractExtendNames: {
				cmd: 'node traverse_files.js ' +  './../' + config.outputFilePath + config.interfaceNamesFile + ' ' + config.relativeUserCode + ' ./' + config.bindingsFileName,
				cwd: './_ast_parser'
			},
			runBindingGenerator: {
				cmd: 'java -jar extend_classes_grator.jar ' + config.jarsDir + ' ' + './../' + config.astParserDir + '/' + config.bindingsFileName + ' ' + config.relativeOutDir,
				cwd: './_binding_generator'
			}
		},
		clean: {
			outDir: {
				src: config.outDir + '/*'
			},
			jsCodeDir: {
				src: config.userCode + '/*'
			}
		},
		mkdir: {
			outDir: {
				options: {
					create: [config.outDir]
				}
			},
			jsCodeDir: {
				options: {
					create: [config.userCode]
				}
			}
		}
	});
	
	grunt.registerTask('default', [
						'clean:outDir',
						'mkdir:outDir',
						'exec:generateInterfaceNameList',
						'exec:npmInstallInParseProject',
						'exec:extractExtendNames',
						'exec:runBindingGenerator'
					]);
	
	//load modules
	grunt.loadNpmTasks('grunt-exec');
	grunt.loadNpmTasks('grunt-contrib-clean');
	grunt.loadNpmTasks('grunt-mkdir');
}
//generates interfaces
// java -jar interface_generator/interface_name_grator.jar ./../jars interface-names.txt

//call parser
// node traverse_files.js ./../_interface_name_generator/out/interface-names.txt ./../JSCODE ./out.txt

//generate bindings
// java -jar extend_classes_grator.jar ./../JARS ./../_ast_parser/out.txt ./out