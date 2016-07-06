/*
 * Copyright 2016 agido GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pageobject.core.browser

import java.util.concurrent.atomic.AtomicReference

import org.pageobject.core.TestHelper
import org.pageobject.core.driver.DriverProvider
import org.pageobject.core.page.AtChecker
import org.pageobject.core.page.PageObject

import scala.util.DynamicVariable

/**
 * While instantiating a Page the PageHolder will be stored here.
 *
 * This allowes a cleaner syntax for PageObjects without passing the PageHolder around.
 *
 * Because of this it is only allowed to create PageObjects for arguments passed to
 * <code>PageBrowser.to(page)</code>, <code>PageBrowser.via(page)</code>, <code>PageBrowser.at(page)</code>,
 * <code>PageBrowser.isAt(page)</code> or <code>UnexpectedPages</code>
 *
 * This allows the PageObject to be completely stateless, only the PageHolder owns a state (the active Page).
 * Because a PageObject needs a reference to the PageHolder to ask for the WebDriver, and this should not be mutable,
 * this information needs to be passed at creation time. Because we do not want to write boilerplate code for
 * PageObjects, this PageHolder object is used.
 **/
object PageHolder {

  private object PageHolder extends DynamicVariable[Option[PageHolder]](None)

  def apply(): PageHolder = PageHolder.value.getOrElse(throw new RuntimeException(
    "No PageHolder is active! Page creation is only allowed in PageBrowser.to/via/at/isat or for UnexpectedPages"))

  def withPageHolder[S](pageHolder: PageHolder)(thunk: => S): S = PageHolder.withValue(Some(pageHolder))(thunk)
}

/**
 * Manages the currently active page, used by PageBrowser
 */
trait PageHolder extends DriverProvider {
  private val activePageReference = new AtomicReference[Option[AtChecker]]()

  /**
   * Marks the active page as inactive.
   */
  protected def clearActivePage(): Unit = {
    activePage = None
  }

  /**
   * Marks the given page as active.
   */
  protected def activePage_=(page: AtChecker): Unit = {
    activePageReference.set(Some(page))
  }

  /**
   * Marks the given page as active.
   */
  protected def activePage_=(page: Option[AtChecker]): Unit = {
    activePageReference.set(page)
  }

  /**
   * @return the active page
   */
  protected def activePage: Option[AtChecker] = {
    activePageReference.get()
  }

  /**
   * Executes the given code while setting page as the active page.
   *
   * @param page the page to activate while executing thunk
   *
   * @param thunk the code to execute
   *
   * @tparam S the type of the return value
   *
   * @return the value returned by thunk
   */
  protected def withActivePage[S](page: AtChecker)(thunk: => S): S = {
    val old = activePage
    activePage = page
    try thunk
    finally activePage = old
  }

  /**
   * returns true if the given page is the active
   *
   * @param page the page to check
   *
   * @return true if the given page is active
   */
  protected[pageobject] def isActivePage(page: AtChecker): Boolean = {
    activePageReference.get().exists(_ eq page)
  }

  /**
   * Marks the active page as inactive.
   */
  protected[pageobject] def invalidateActivePage(): Unit = {
    activePageReference.set(None)
  }

  /**
   * Marks the given page as inactive.
   */
  protected[pageobject] def invalidatePage(pageObject: PageObject): Unit = {
    failOnInactivePage(pageObject)
    invalidateActivePage()
  }

  /**
   * Abort the test when the given PageObject is not the active one.
   *
   * @param pageObject the PageObject to test
   */
  protected[pageobject] def failOnInactivePage(pageObject: PageObject): Unit = {
    if (!isActivePage(pageObject)) {
      TestHelper.failTest("page object is not active!")
    }
  }
}