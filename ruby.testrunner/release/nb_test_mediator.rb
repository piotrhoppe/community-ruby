# 
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
# 
# Copyright 2008 Sun Microsystems, Inc. All rights reserved.
# 
# The contents of this file are subject to the terms of either the GNU
# General Public License Version 2 only ("GPL") or the Common
# Development and Distribution License("CDDL") (collectively, the
# "License"). You may not use this file except in compliance with the
# License. You can obtain a copy of the License at
# http://www.netbeans.org/cddl-gplv2.html
# or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
# specific language governing permissions and limitations under the
# License.  When distributing the software, include this License Header
# Notice in each file and include the License file at
# nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the GPL Version 2 section of the License file that
# accompanied this code. If applicable, add the following below the
# License Header, with the fields enclosed by brackets [] replaced by
# your own identifying information:
# "Portions Copyrighted [year] [name of copyright owner]"
# 
# If you wish your version of this file to be governed by only the CDDL
# or only the GPL Version 2, indicate your decision by adding
# "[Contributor] elects to include this software in this distribution
# under the [CDDL or GPL Version 2] license." If you do not indicate a
# single choice of license, a recipient has the option to distribute
# your version of this file under either the CDDL, the GPL Version 2 or
# to extend the choice of license to its licensees as provided above.
# However, if you add GPL Version 2 code and therefore, elected the GPL
# Version 2 license, then the option applies only if the new code is
# made subject to such option by the copyright holder.
# 
# Contributor(s):
# 
# Portions Copyrighted 2008 Sun Microsystems, Inc.
 
require 'test/unit'
require 'test/unit/testcase'
require 'test/unit/testsuite'
require 'test/unit/ui/testrunnermediator'
require 'getoptlong'
require 'rubygems'
require 'rake'

class NbTestMediator

  def parse_args
    @suites = []

    parser = GetoptLong.new
    parser.set_options(
      ["-f", "--file", GetoptLong::OPTIONAL_ARGUMENT],
      ["-d", "--directory", GetoptLong::OPTIONAL_ARGUMENT],
      ["-m", "--testmethod", GetoptLong::OPTIONAL_ARGUMENT]
    )
    
    loop do
      begin
        opt, arg = parser.get
        break if not opt
        case opt
        # single file
        when "-f"
          add_to_suites arg
        # directory
        when "-d"
          Rake::FileList["#{arg}/**/*.rb"].each { |file| add_to_suites(file) }
        # single test method
        when "-m"
          if "-m" != ""
            @suites.each do |s| 
              tests_to_delete = []
              s.tests.each do |t|
                unless t.method_name == arg 
                  tests_to_delete << t
                end
              end
              tests_to_delete.each do |t|
                s.delete(t)
              end
            end
          end
        end
      end
    end
  end

  def add_to_suites file_name
    file_name = file_name[0..file_name.length - 4]
    require "#{file_name}"
    last_slash = file_name.rindex(File::SEPARATOR)
     # try ALT_SEPARATOR for some Windows versions
    last_slash = file_name.rindex(File::ALT_SEPARATOR) unless last_slash
    if last_slash
      test_class = file_name[last_slash + 1..file_name.length]
    else 
      test_class = file_name
    end
    begin
      instance = Object.const_get(camelize(test_class))
    rescue NameError
      return
    end
    if (instance.respond_to?(:suite))
      @suites << instance.suite
    elsif instance.kind_of? Test::Unit::TestSuite
      @suites << instance
    end

  end

  def get_filename class_name
    class_name.to_s.gsub(/::/, '/').
      gsub(/([A-Z]+)([A-Z][a-z])/,'\1_\2').
      gsub(/([a-z\d])([A-Z])/,'\1_\2').
      tr("-", "_").
      downcase
  end
  
  def camelize(lower_case_and_underscored_word, first_letter_in_uppercase = true)
    if first_letter_in_uppercase
      lower_case_and_underscored_word.to_s.gsub(/\/(.?)/) { "::" + $1.upcase }.gsub(/(^|_)(.)/) { $2.upcase }
    else
      lower_case_and_underscored_word.first + camelize(lower_case_and_underscored_word)[1..-1]
    end
  end

  def run_mediator
    parse_args
    
    @suites.each do |suite| 
      @mediator = Test::Unit::UI::TestRunnerMediator.new(suite)
      attach_listeners
      begin
        puts "%SUITE_STARTING% #{suite}"
        start_suite_timer
        result = @mediator.run_suite
        puts "%SUITE_SUCCESS% #{result.passed?}"
        puts "%SUITE_FAILURES% #{result.failure_count}"
        puts "%SUITE_ERRORS% #{result.error_count}"
      rescue => err
        puts "%SUITE_FINISHED% time=#{elapsed_suite_time}"
        puts "%SUITE_ERROR_OUTPUT% error=#{err}"
      end
    end
    
  end
  
  def start_suite_timer
    @suite_start_time = Time.now
  end
  
  def elapsed_suite_time
    Time.now - @suite_start_time
  end
  def attach_listeners
    @mediator.add_listener(Test::Unit::UI::TestRunnerMediator::STARTED, &method(:suite_started))
    @mediator.add_listener(Test::Unit::UI::TestRunnerMediator::FINISHED, &method(:suite_finished))
    @mediator.add_listener(Test::Unit::TestResult::CHANGED, &method(:test_result_changed))
    @mediator.add_listener(Test::Unit::TestResult::FAULT, &method(:test_fault))
    @mediator.add_listener(Test::Unit::TestCase::STARTED, &method(:test_started))
    @mediator.add_listener(Test::Unit::TestCase::FINISHED, &method(:test_finished))
  end
  
  def test_fault(result)
    if (result.instance_of?(Test::Unit::Failure))
      puts "%TEST_FAILED% time=#{elapsed_time} testname=#{result.test_name} message=#{result.message.to_s.gsub($/, " ")} location=#{result.location}"
    else
      stacktrace = result.exception.backtrace.join("%BR%")
      puts "%TEST_ERROR% time=#{elapsed_time} testname=#{result.test_name} message=#{result.message.to_s.gsub($/, " ")} location=#{stacktrace}"
    end
  end
  
  def test_result_changed(result)
    puts "%TEST_RESULT_CHANGED% #{result}"
  end
  
  def suite_started(result)
    puts "%SUITE_STARTED% #{result}"
  end

  def suite_finished(result)
    puts "%SUITE_FINISHED% time=#{result}"
  end
  
  def test_started(result)
    start_timer
    puts "%TEST_STARTED% #{result}"
  end

  def test_finished(result)
    puts "%TEST_FINISHED% time=#{elapsed_time} #{result}"
  end
  
  def start_timer
    @start_time = Time.now
  end
  
  def elapsed_time
    Time.now - @start_time
  end
end

NbTestMediator.new.run_mediator